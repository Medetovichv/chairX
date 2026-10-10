import { PageTabs } from '../../../shared/components/PageTabs';
import { useAuth } from '../../auth/AuthProvider';
import { routeFor, childrenOf } from '../../../shared/lib/navigation';
export function ReturnsTabs(){
 const {hasPermission}=useAuth();
 const root=routeFor('/returns')!;
 const primary=hasPermission('RETURNS_READ')?[{...root,label:'Возвраты',permissions:['RETURNS_READ']}]:[];
 const tabs=[...primary,...childrenOf('/returns').filter(t=>t.permissions.some(hasPermission))];
 return <PageTabs tabs={tabs}/>;
}
