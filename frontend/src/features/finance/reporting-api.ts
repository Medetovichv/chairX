import {apiClient} from '../../shared/api/client';
type Money=string|number;
const params=(from:string,to:string)=>new URLSearchParams({from,to}).toString();
export interface Profitability {
 from:string;to:string;grossRevenue:Money;verifiedFifoCost:Money;
 recognizedAccruals:Money;otherOperatingExpenses:Money;returnedRevenue:Money;
 refundOnlyDiscounts:Money;restoredFifoCost:Money;netRevenue:Money;
 completeCost:boolean;netProfitAvailable:boolean;grossProfit:Money|null;
 operatingProfit:Money|null;caveat:string|null;
 sales:{saleId:string;saleNumber:string;recognizedRevenue:Money;fifoCost:Money;completeCost:boolean}[];
}
export interface LedgerLine {id:string;postedAt:string;businessDate:string;account:string;amount:Money;type:string;sourceType:string;sourceId:string|null;createdBy:string}
export interface LedgerPage {items:LedgerLine[];total:number;page:number;size:number}
export interface Margin {modelId:string;model:string;variantId:string;variation:string;quantity:number;revenue:Money;knownFifoCost:Money;costComplete:boolean;grossProfit:Money|null}
export interface Valuation {totalCost:Money;quantity:number;models:{productId:string;model:string;quantity:number;cost:Money}[]}
export interface Obligations {totalOutstanding:Money;totalPrepaid:Money;purchases:{purchaseId:string;supplierName:string;createdAt:string;status:string;supplierTotal:Money;cargoTotal:Money;supplierPaid:Money;cargoPaid:Money;outstanding:Money;prepaid:Money}[]}
export interface AnalyticsMonth {from:string;recognizedRevenue:Money;netRevenue:Money;returnedRevenue:Money;refundDiscounts:Money;fifoCost:Money;restoredFifoCost:Money;grossProfit:Money|null;operatingProfit:Money|null;profitVerified:boolean;accrualExpenses:Money;otherExpenses:Money;cashIn:Money;cashOut:Money;netCash:Money}
export interface Analytics {from:string;to:string;months:AnalyticsMonth[]}
export const reportingApi={
 profitability:(from:string,to:string)=>apiClient.get<Profitability>('/api/finance/profitability?'+params(from,to)),
 ledger:(from:string,to:string,page:number,account:string,movementType='',search='',minAmount='',maxAmount='',sort='newest')=>apiClient.get<LedgerPage>('/api/finance/ledger?'+new URLSearchParams({from,to,page:String(page),size:'50',sort,...(account?{account}:{}),...(movementType?{movementType}:{}),...(search?{search}:{}),...(minAmount?{minAmount}:{}),...(maxAmount?{maxAmount}:{})})),
 margins:(from:string,to:string)=>apiClient.get<Margin[]>('/api/finance/model-margins?'+params(from,to)),
 valuation:()=>apiClient.get<Valuation>('/api/finance/inventory-valuation'),
 obligations:()=>apiClient.get<Obligations>('/api/finance/purchase-obligations'),
 analytics:(from:string,to:string)=>apiClient.get<Analytics>('/api/finance/analytics?'+params(from,to)),
};