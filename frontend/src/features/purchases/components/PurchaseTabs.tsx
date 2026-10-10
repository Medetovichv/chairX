import { Link, useLocation } from 'react-router';
import { useAuth } from '../../auth/AuthProvider';
const tabs = [
  {path:'/purchases', label:'Закупки', permission:'PURCHASE_READ'},
  {path:'/purchases/receipts', label:'Поступления', permission:'INVENTORY_RECEIVE'},
  {path:'/purchases/payments', label:'Оплаты', permission:'PURCHASE_PAYMENTS_READ'},
  {path:'/purchases/suppliers', label:'Поставщики', permission:'SUPPLIERS_READ'},
] as const;
export function PurchaseTabs() {
  const { pathname } = useLocation();
  const { hasPermission } = useAuth();
  return <nav aria-label="Разделы закупок" className="flex flex-wrap gap-2">
    {tabs.filter(t=>hasPermission(t.permission)).map(t=><Link key={t.path} to={t.path}
      aria-current={pathname===t.path?'page':undefined}
      className={'inline-flex min-h-10 items-center rounded-xl px-4 text-sm font-semibold ' +
        (pathname===t.path?'bg-indigo-600 text-white':'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50')}>
      {t.label}</Link>)}
  </nav>;
}
