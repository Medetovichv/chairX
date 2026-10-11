import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi, catalogKeys } from '../../catalog/api';

/**
 * A safe, readable name for an item of an existing sale/return.
 * Respects dynamic CATALOG_READ and uses actual catalog records only.
 * UUID stays in the underlying React query key and link, never in text.
 */
export function ReturnChairLabel({variantId}:{variantId:string}){
  const {hasPermission}=useAuth();
  const canRead=hasPermission('CATALOG_READ')&&Boolean(variantId);
  const variant=useQuery({
    queryKey:['catalog','variant',variantId],
    queryFn:()=>catalogApi.getVariant(variantId),
    enabled:canRead,
  });
  const modelId=variant.data?.productId??'';
  const model=useQuery({
    queryKey:catalogKeys.product(modelId),
    queryFn:()=>catalogApi.getProduct(modelId),
    enabled:canRead&&!!modelId,
  });
  if(!canRead) return <span>Кресло · название доступно в каталоге</span>;
  if(variant.isPending) return <span>Загружаем кресло…</span>;
  if(variant.isError||!variant.data) return <span>Не удалось загрузить название кресла</span>;
  return <span className="inline-flex flex-wrap items-baseline gap-1">
    {model.data?
      <Link to={'/catalog/'+encodeURIComponent(model.data.id)}
        className="font-semibold text-blue-700 underline-offset-2 hover:underline focus-visible:underline">
        {model.data.name}</Link>:
      <span className="font-semibold">Кресло</span>}
    <span>· {variant.data.name}</span>
    {variant.data.sku&&<span className="text-xs font-normal text-slate-500">
      · Артикул: {variant.data.sku}</span>}
  </span>;
}
