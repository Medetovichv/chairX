import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiClientError, userFacingApiError } from '../../../shared/api/api-error';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { inventoryApi, inventoryKeys } from '../api';
import { positiveQuantity, warehouseLabel, warehouseStock } from '../lib';
import type { StockLine, TransferInput, Warehouse } from '../types';

type TransferPhase = 'editing' | 'confirm' | 'sending' | 'uncertain';

export function TransferForm({ line, warehouses, preferredWarehouseId, onClose, onSuccess }: {
  line: StockLine; warehouses: Warehouse[]; preferredWarehouseId: string | null;
  onClose: () => void; onSuccess: (notice: string) => void;
}) {
  const client = useQueryClient();
  const active = warehouses.filter((w) => w.active);
  const [source, setSource] = useState(preferredWarehouseId && active.some((w) => w.id === preferredWarehouseId)
    ? preferredWarehouseId : '');
  const [destination, setDestination] = useState('');
  const [quantity, setQuantity] = useState('');
  const [phase, setPhase] = useState<TransferPhase>('editing');
  const [payload, setPayload] = useState<TransferInput | null>(null);
  const [problem, setProblem] = useState('');
  const inFlight = useRef(false);
  const available = source ? (warehouseStock(line, source)?.available ?? 0) : 0;

  async function completed() {
    await Promise.all([
      client.invalidateQueries({ queryKey: inventoryKeys.overviewPrefix }),
      client.invalidateQueries({ queryKey: inventoryKeys.balancePrefix }),
      client.invalidateQueries({ queryKey: inventoryKeys.movementsPrefix }),
      client.invalidateQueries({ queryKey: inventoryKeys.transfersPrefix }),
    ]);
    onSuccess('Перемещение выполнено. Остатки и история обновлены.');
  }

  function prepare() {
    setProblem('');
    if (!source || !destination) { setProblem('Выберите оба склада.'); return; }
    if (source === destination) { setProblem('Склад отправления и назначения должны отличаться.'); return; }
    if (!active.some((w) => w.id === source) || !active.some((w) => w.id === destination)) {
      setProblem('Нельзя использовать неактивный склад.'); return;
    }
    const error = positiveQuantity(quantity, available);
    if (error) { setProblem(error); return; }
    // Freeze the full operation. All retries MUST use the exact same payload.
    setPayload({
      transferId: crypto.randomUUID(), sourceWarehouseId: source,
      destinationWarehouseId: destination, variantId: line.productVariantId,
      quantity: Number(quantity),
    });
    setPhase('confirm');
  }

  async function sendTransfer() {
    if (!payload || inFlight.current) return;
    inFlight.current = true;
    setPhase('sending');
    setProblem('');
    try {
      await inventoryApi.transfer(payload);
      await completed();
    } catch (error) {
      if (error instanceof ApiClientError &&
          (error.kind === 'network' || error.kind === 'parse' ||
            (error.kind === 'http' && (error.status ?? 0) >= 500))) {
        setPhase('uncertain');
        setProblem('Ответ сервера не получен. Перемещение могло состояться. Проверьте операцию или повторите тот же запрос — новый ID не создаётся.');
      } else {
        setPhase('confirm');
        setProblem(userFacingApiError(error));
      }
    } finally {
      inFlight.current = false;
    }
  }

  async function checkTransfer() {
    if (!payload || inFlight.current) return;
    inFlight.current = true;
    setPhase('sending');
    setProblem('');
    try {
      const recorded = await inventoryApi.transferById(payload.transferId);
      if (recorded.sourceWarehouseId !== payload.sourceWarehouseId ||
          recorded.destinationWarehouseId !== payload.destinationWarehouseId ||
          recorded.variantId !== payload.variantId || recorded.quantity !== payload.quantity) {
        setPhase('uncertain');
        setProblem('Данные найденной операции не совпадают. Обратитесь к администратору; повторять операцию нельзя.');
        return;
      }
      await completed();
    } catch (error) {
      setPhase('uncertain');
      if (error instanceof ApiClientError && error.status === 404) {
        setProblem('Операция пока не найдена. Повторить можно только с прежним ID и без изменения данных.');
      } else {
        setProblem('Не удалось проверить результат: ' + userFacingApiError(error));
      }
    } finally {
      inFlight.current = false;
    }
  }

  const locked = phase === 'sending' || phase === 'uncertain';
  return (
    <section aria-label="Перемещение товара" className="space-y-4 rounded-2xl border border-indigo-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-bold">Перемещение товара</h3>
          <p className="mt-1 text-sm text-slate-600">{line.model} / {line.variation}</p>
        </div>
        <Button variant="outline" disabled={locked} onClick={onClose}>Закрыть форму</Button>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-2 text-sm font-semibold">Склад отправления
          <select aria-label="Склад отправления" className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"
            value={source} disabled={phase !== 'editing'} onChange={(event) => setSource(event.target.value)}>
            <option value="">Выберите склад</option>
            {active.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
        </label>
        <label className="space-y-2 text-sm font-semibold">Склад назначения
          <select aria-label="Склад назначения" className="min-h-11 w-full rounded-xl border border-slate-200 bg-white px-3"
            value={destination} disabled={phase !== 'editing'} onChange={(event) => setDestination(event.target.value)}>
            <option value="">Выберите склад</option>
            {active.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
        </label>
        <label className="space-y-2 text-sm font-semibold">Количество, шт.
          <Input aria-label="Количество, шт." inputMode="numeric" value={quantity}
            disabled={phase !== 'editing'} onChange={(event) => setQuantity(event.target.value)}
            placeholder="Введите количество" />
        </label>
        <div className="flex flex-col justify-end rounded-xl bg-slate-50 p-3 text-sm">
          <span className="text-slate-500">Доступно на складе отправления</span>
          <strong className="mt-1 text-lg">{source ? available : 'Выберите склад'} шт.</strong>
        </div>
      </div>
      {problem && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{problem}</p>}
      {phase === 'editing' && (
        <div className="flex gap-2">
          <Button onClick={prepare}>Проверить перемещение</Button>
          <Button variant="outline" onClick={onClose}>Отмена</Button>
        </div>
      )}
      {phase === 'confirm' && payload && (
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setPhase('editing')}>Изменить данные</Button>
          <Button variant="outline" onClick={onClose}>Отмена</Button>
        </div>
      )}
      {phase === 'sending' && <p role="status" className="text-sm font-semibold text-indigo-700">Проверяем или сохраняем перемещение…</p>}
      {phase === 'uncertain' && (
        <div className="space-y-2">
          <p className="text-sm font-semibold text-amber-900">Неизвестен итог операции {payload?.transferId}.</p>
          <p className="text-sm text-slate-600">Не закрывайте форму и не оформляйте новое перемещение, пока результат не установлен.</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={() => void checkTransfer()}>Проверить по ID</Button>
            <Button type="button" variant="outline" onClick={() => void sendTransfer()}>Повторить прежний запрос</Button>
          </div>
        </div>
      )}
      <ConfirmDialog open={phase === 'confirm' && problem === '' && payload !== null}
        onOpenChange={(open) => { if (!open && phase === 'confirm') setPhase('editing'); }}
        title={'Переместить ' + (payload?.quantity ?? '') + ' шт.'}
        description={line.model + ' / ' + line.variation +
          '. Со склада: ' + warehouseLabel(payload?.sourceWarehouseId ?? '', warehouses) +
          '. На склад: ' + warehouseLabel(payload?.destinationWarehouseId ?? '', warehouses) + '.'}
        confirmLabel="Подтвердить" busy={phase === 'sending'} onConfirm={() => void sendTransfer()} />
    </section>
  );
}
