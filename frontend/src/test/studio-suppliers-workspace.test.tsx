import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SuppliersWorkspaceOverview } from '../features/purchases/components/SuppliersWorkspaceOverview';
import type { Supplier } from '../features/purchases/types';

const supplier=(id:string,active:boolean,contact:string|null):Supplier=>({
  id,name:'Поставщик '+id,contactInformation:contact,comment:null,active,
  createdAt:'2026-10-10T00:00:00Z',updatedAt:'2026-10-10T00:00:00Z'
});

describe('Studio suppliers overview',()=>{
  it('uses only server-returned supplier cards with explicit page disclosure',()=>{
    render(<SuppliersWorkspaceOverview suppliers={[
      supplier('one',true,'+996 700 123456'),
      supplier('two',false,null),
      supplier('three',true,'wechat'),
    ]} total={72} page={2}/>);
    expect(screen.getByText('Страница 3 · 3 из 72')).toBeInTheDocument();
    expect(screen.getByText('2',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText('1',{selector:'strong'})).toBeInTheDocument();
    expect(screen.getByText(/только по текущей странице/)).toBeInTheDocument();
  });
  it('does not claim global supplier totals based on an empty page',()=>{
    render(<SuppliersWorkspaceOverview suppliers={[]} total={31} page={1}/>);
    expect(screen.getAllByText('0',{selector:'strong'})).toHaveLength(3);
    expect(screen.getByText('Страница 2 · 0 из 31')).toBeInTheDocument();
  });
});
