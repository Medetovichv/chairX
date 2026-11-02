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
      className={'inline-flex min-h-11 items-center rounded-full px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-[#416c82] ' +
        (pathname===t.path?'bg-[#182d38] text-white':'border border-[#dce6eb] bg-white text-[#516977] hover:bg-[#edf3f6]')}>
      {t.label}</Link>)}
  </nav>;
}
