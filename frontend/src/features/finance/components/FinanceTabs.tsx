import { PageTabs } from '../../../shared/components/PageTabs';
import { useAuth } from '../../auth/AuthProvider';
import { routeFor,childrenOf } from '../../../shared/lib/navigation';
export function FinanceTabs(){
 const {hasPermission}=useAuth();
 const root=routeFor('/finance')!;
 const tabs=[...(hasPermission('FINANCE_READ')?[root]:[]),
 ...childrenOf('/finance').filter(x=>x.permissions.some(hasPermission))];
 return <PageTabs tabs={tabs}/>;
}
