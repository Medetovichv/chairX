import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../shared/api/query';
import { authSession } from '../features/auth/session';
import { PurchasePaymentForm } from '../features/purchases/components/PurchasePaymentForm';
import {
  isValidPaymentAmount, matchesCommittedPayment, preparePayment, validatePaymentAmount,
} from '../features/purchases/payment-validation';
import type { CreatePurchasePayment, Payment, PaymentSummary } from '../features/purchases/types';

const access = vi.hoisted(() => ({
  permissions: ['PURCHASE_PAYMENTS_READ', 'PURCHASE_PAYMENTS_CREATE'] as string[],
}));
vi.mock('../features/auth/AuthProvider', () => ({
  useAuth: () => ({
    hasPermission: (p: string) => access.permissions.includes(p),
    hasAnyPermission: (ps: readonly string[]) => ps.some(p => access.permissions.includes(p)),
    isAuthenticated: true,
    status: 'authenticated',
    user: { roles: ['CUSTOM_FINANCE_OPERATOR'], permissions: access.permissions },
  }),
}));

const PID = '12341234-1234-4123-8123-123412341234';
const MOMENT = '2026-10-10T10:00:00Z';
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const initial = (): PaymentSummary => ({
  purchaseId: PID,
  supplierTotal: '35000.00', cargoTotal: '800.00',
  paidSupplier: '5000.00', paidCargo: '0.00',
  remainingSupplier: '30000.00', remainingCargo: '800.00',
  payments: [],
});
const paid = (request: CreatePurchasePayment): Payment => ({
  id: '33333333-3333-4333-8333-333333333333', purchaseId: PID,
  paymentKind: request.paymentKind, account: request.account, amount: request.amount,
  idempotencyKey: request.idempotencyKey, reference: request.reference,
  comment: request.comment, createdBy: 'accountant', createdAt: MOMENT,
});
function simulate(options: {
  failAfterCommitOnce?: boolean;
  httpStatusOnce?: number;
  uninitialized?: boolean;
  slow?: Promise<void>;
} = {}) {
  const summary = initial();
  const byKey = new Map<string, Payment>();
  let networkOnce = Boolean(options.failAfterCommitOnce);
  let httpOnce = options.httpStatusOnce ?? 0;
  let debits = 0;
  const fetchMock = vi.fn(async (raw: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(String(raw), 'http://chairx.local');
    const method = init?.method || 'GET';
    if (url.pathname === '/api/finance/accounts' && method === 'GET') {
      return json([
        { code: 'CASH', balance: '50000.00', initialized: !options.uninitialized },
        { code: 'BANK', balance: '90000.00', initialized: !options.uninitialized },
      ]);
    }
    if (url.pathname === '/api/purchases/' + PID + '/payments') {
      if (method === 'GET') {
        return json({ ...summary, payments: [...byKey.values()] });
      }
      if (method === 'POST') {
        await options.slow;
        if (httpOnce) {
          const failure = httpOnce; httpOnce = 0;
          return json({ code: 'TEST_FAILURE', message: 'secret internal message', details: {} }, failure);
        }
        const payload = JSON.parse(String(init?.body)) as CreatePurchasePayment;
        let result = byKey.get(payload.idempotencyKey);
        if (!result) {
          result = paid(payload);
          byKey.set(payload.idempotencyKey, result);
          debits++;
          if (payload.paymentKind === 'SUPPLIER') {
            summary.remainingSupplier = String(Number(summary.remainingSupplier) - Number(payload.amount));
          } else {
            summary.remainingCargo = String(Number(summary.remainingCargo) - Number(payload.amount));
          }
        }
        if (networkOnce) {
          networkOnce = false;
          throw new TypeError('socket closed after finance commit');
        }
        return json(result, 201);
      }
    }
    throw new Error('Unmocked request: ' + method + ' ' + url.pathname);
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    calls: fetchMock, byKey, summary,
    debits: () => debits,
    paymentPosts: () => fetchMock.mock.calls.filter(([url, init]) =>
      String(url) === '/api/purchases/' + PID + '/payments' &&
      (init as RequestInit).method === 'POST'),
    financeGets: () => fetchMock.mock.calls.filter(([url]) =>
      String(url) === '/api/finance/accounts'),
  };
}

function renderPayment(summary = initial()) {
  const posted = vi.fn<(payment: Payment) => void>();
  const view = render(<QueryClientProvider client={createQueryClient()}>
    <PurchasePaymentForm purchaseId={PID} summary={summary} onPosted={posted} />
  </QueryClientProvider>);
  return { posted, ...view };
}
async function inputAndConfirm({
  kind = 'SUPPLIER', account = 'BANK', amount = '15000',
  reference = 'MBANK-123', comment = 'Партия #1',
}: { kind?: string; account?: string; amount?: string; reference?: string; comment?: string } = {}) {
  const form = within(screen.getByRole('form', { name: 'Новая оплата закупки' }));
  fireEvent.change(form.getByRole('combobox', { name: 'Категория оплаты' }), { target: { value: kind } });
  fireEvent.change(form.getByRole('combobox', { name: 'Счёт оплаты' }), { target: { value: account } });
  fireEvent.change(form.getByRole('textbox', { name: 'Сумма оплаты в сомах' }), { target: { value: amount } });
  fireEvent.change(form.getByRole('textbox', { name: 'Номер перевода' }), { target: { value: reference } });
  fireEvent.change(form.getByRole('textbox', { name: 'Комментарий оплаты' }), { target: { value: comment } });
  await userEvent.setup().click(form.getByRole('button', { name: 'Проверить и подтвердить оплату' }));
  return form;
}
async function accept() {
  await userEvent.setup().click(within(screen.getByRole('alertdialog'))
    .getByRole('button', { name: 'Провести оплату' }));
}

beforeEach(() => {
  access.permissions = ['PURCHASE_PAYMENTS_READ', 'PURCHASE_PAYMENTS_CREATE'];
  authSession.activate('Basic payment-test-user', { headerName: 'X-CSRF-TOKEN', token: 'csrf-test' });
});
afterEach(() => { authSession.clear(); vi.unstubAllGlobals(); });

describe('F06.1 exact validation and money safety', () => {
  it('requires positive integer som and max 17 digits', () => {
    for (const bad of ['0', '-10', '0.01', '1.00', '2,5', '01', 'abc',
      '100000000000000000', '', ' ', '1e3']) expect(isValidPaymentAmount(bad)).toBe(false);
    for (const ok of ['1', '30000', '99999999999999999']) expect(isValidPaymentAmount(ok)).toBe(true);
    expect(validatePaymentAmount('30001', '30000.00')).toMatch(/превышает/);
    expect(validatePaymentAmount('30000', '30000.99')).toBeNull();
    expect(validatePaymentAmount('1', '0.99')).toMatch(/превышает/);
    expect(validatePaymentAmount('1', null)).toMatch(/Невозможно точно/);
    expect(validatePaymentAmount('1', Number.MAX_SAFE_INTEGER + 1)).toMatch(/Невозможно точно/);
    expect(validatePaymentAmount('9007199254740992', '9007199254740993.00')).toBeNull();
  });
  it('canonicalizes optional fields and keeps immutable exact payment body', () => {
    const summary = initial();
    const {payload,error} = preparePayment('CARGO', 'CASH', '250',
      '  REF-22 ', '  Товар  ', summary, 'custom-key');
    expect(error).toBeNull();
    expect(payload).toEqual({
      idempotencyKey: 'custom-key', paymentKind: 'CARGO', account: 'CASH',
      amount: '250', reference: 'REF-22', comment: 'Товар',
    });
    expect(matchesCommittedPayment({idempotencyKey:'custom-key',account:'CASH',
      paymentKind:'CARGO',amount:'250.00'},payload!)).toBe(true);
    expect(matchesCommittedPayment({idempotencyKey:'custom-key',account:'BANK',
      paymentKind:'CARGO',amount:'250.00'},payload!)).toBe(false);
    expect(preparePayment('SUPPLIER','', '1','','',summary,'id').error).toMatch(/счёт/);
    expect(preparePayment('CARGO','BANK','801','','',summary,'id').error).toMatch(/превышает/);
  });
});

describe('F06.1 UI permission and payment posting', () => {
  it('create permission without read never reveals a payment form or calls finance', () => {
    access.permissions = ['PURCHASE_PAYMENTS_CREATE', 'FINANCE_READ'];
    const api = simulate();
    const { container } = renderPayment();
    expect(container).toBeEmptyDOMElement();
    expect(api.calls).not.toHaveBeenCalled();
  });
  it('read-only roles never see POST controls; custom role names do not matter', () => {
    access.permissions = ['PURCHASE_PAYMENTS_READ'];
    const api = simulate();
    const {container}=renderPayment();
    expect(container).toBeEmptyDOMElement();
    expect(api.calls).not.toHaveBeenCalled();
  });
  it('cannot fetch account balances without FINANCE_READ', async () => {
    const api = simulate();
    renderPayment();
    expect(screen.getByRole('heading', {name:'Провести оплату'})).toBeInTheDocument();
    expect(api.financeGets()).toHaveLength(0);
  });
  it('shows balances with FINANCE_READ and locks uninitialized account', async () => {
    access.permissions.push('FINANCE_READ');
    const api = simulate({uninitialized:true});
    renderPayment();
    await screen.findByRole('combobox', {name:'Счёт оплаты'});
    fireEvent.change(screen.getByRole('combobox', {name:'Счёт оплаты'}), {target:{value:'BANK'}});
    expect(await screen.findByText(/не инициализирован — оплата заблокирована/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name:'Проверить и подтвердить оплату'})).toBeDisabled();
    expect(api.financeGets()).toHaveLength(1);
  });
  it('requires explicit confirmation; posts once with selected account, kind and immutable id', async () => {
    const api = simulate();
    const {posted}=renderPayment();
    await inputAndConfirm();
    expect(screen.getByRole('alertdialog')).toHaveTextContent('15000 сом');
    expect(screen.getByRole('alertdialog')).toHaveTextContent('BANK');
    expect(api.paymentPosts()).toHaveLength(0);
    await accept();
    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(1);
    const body = JSON.parse(String((api.paymentPosts()[0]?.[1] as RequestInit).body)) as CreatePurchasePayment;
    expect(body).toMatchObject({
      paymentKind:'SUPPLIER',account:'BANK',amount:'15000',
      reference:'MBANK-123',comment:'Партия #1',
    });
    expect(body.idempotencyKey).toMatch(/^[0-9a-f]{8}-/);
    expect(api.debits()).toBe(1);
  });
  it('records cargo separately in CASH and never uses purchase-read API', async () => {
    const api = simulate();
    const {posted}=renderPayment();
    await inputAndConfirm({kind:'CARGO',account:'CASH',amount:'300'});
    expect(screen.getByRole('alertdialog')).toHaveTextContent('за карго');
    await accept();
    await waitFor(() => expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(1);
    const body = JSON.parse(String((api.paymentPosts()[0]?.[1] as RequestInit).body)) as CreatePurchasePayment;
    expect(body.paymentKind).toBe('CARGO');
    expect(body.account).toBe('CASH');
    expect(api.calls.mock.calls.every(([url]) => !String(url).includes('/api/purchases/overview'))).toBe(true);
    expect(api.financeGets()).toHaveLength(0);
  });
  it('never submits invalid fractions, overpayments or unknown cargo balance', async () => {
    const api = simulate();
    renderPayment();
    await inputAndConfirm({amount:'31000'});
    expect(await screen.findByRole('alert')).toHaveTextContent(/превышает/);
    expect(api.paymentPosts()).toHaveLength(0);
    const field=screen.getByRole('textbox',{name:'Сумма оплаты в сомах'});
    fireEvent.change(field,{target:{value:'19.99'}});
    await userEvent.setup().click(screen.getByRole('button', {name:'Проверить и подтвердить оплату'}));
    expect(await screen.findByRole('alert')).toHaveTextContent(/целое число/);
    expect(api.paymentPosts()).toHaveLength(0);
  });
  it('returns 409 without claiming success or inventing an automatic retry', async () => {
    const api = simulate({httpStatusOnce:409});
    const {posted}=renderPayment();
    await inputAndConfirm(); await accept();
    expect(await screen.findByRole('alert')).toHaveTextContent(/409|противоречит/);
    expect(posted).not.toHaveBeenCalled();
    expect(api.paymentPosts()).toHaveLength(1);
    expect(screen.queryByRole('button',{name:'Повторить исходную операцию'})).not.toBeInTheDocument();
  });
  it('network uncertainty blocks editing and replays byte-identical POST with same key', async () => {
    const api = simulate({failAfterCommitOnce:true});
    const {posted}=renderPayment();
    await inputAndConfirm({amount:'14000'}); await accept();
    const retry=await screen.findByRole('button',{name:'Повторить исходную операцию'});
    expect(screen.getByRole('textbox',{name:'Сумма оплаты в сомах'})).toBeDisabled();
    expect(screen.getByRole('button',{name:'Проводим…'})).toBeDisabled();
    expect(api.debits()).toBe(1);
    await userEvent.setup().click(retry);
    await waitFor(()=>expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(2);
    expect((api.paymentPosts()[0]?.[1] as RequestInit).body)
      .toEqual((api.paymentPosts()[1]?.[1] as RequestInit).body);
    expect(api.byKey.size).toBe(1);
    expect(api.debits()).toBe(1);
  });
  it('reconciles uncertain outcome from server payment history without reposting', async () => {
    const api = simulate({failAfterCommitOnce:true});
    const {posted}=renderPayment();
    await inputAndConfirm({amount:'12000'}); await accept();
    const verify=await screen.findByRole('button',{name:'Проверить по истории'});
    await userEvent.setup().click(verify);
    await waitFor(()=>expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(1);
    expect(api.byKey.size).toBe(1);
    expect(api.debits()).toBe(1);
  });
  it('5xx also locks payment instead of creating a second UUID',async()=>{
    const api=simulate({httpStatusOnce:500});
    const {posted}=renderPayment();
    await inputAndConfirm();await accept();
    const retry=await screen.findByRole('button',{name:'Повторить исходную операцию'});
    expect(api.paymentPosts()).toHaveLength(1);
    expect(posted).not.toHaveBeenCalled();
    await userEvent.setup().click(retry);
    await waitFor(()=>expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(2);
    expect((api.paymentPosts()[0]?.[1] as RequestInit).body)
      .toEqual((api.paymentPosts()[1]?.[1] as RequestInit).body);
  });
  it('locks duplicate action while an unresolved payment request is pending',async()=>{
    let release!:()=>void;
    const wait = new Promise<void>(resolve=>{release=resolve;});
    const api=simulate({slow:wait});
    const {posted}=renderPayment();
    await inputAndConfirm();await accept();
    expect(screen.getByRole('button',{name:'Проверить и подтвердить оплату'})).toBeDisabled();
    release();
    await waitFor(()=>expect(posted).toHaveBeenCalledTimes(1));
    expect(api.paymentPosts()).toHaveLength(1);
  });
});
