import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '../lib/cn';

const variants = cva(
  'inline-flex min-h-11 shrink-0 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#416c82] disabled:cursor-not-allowed disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-[#182d38] text-white hover:bg-[#305063]',
        outline: 'border border-[#dce6eb] bg-white text-[#182d38] hover:bg-[#edf3f6]',
        ghost: 'text-[#516977] hover:bg-[#edf3f6]',
        destructive: 'bg-red-600 text-white hover:bg-red-700',
      },
      size: {
        default: 'px-4',
        sm: 'min-h-9 px-3',
        icon: 'min-h-11 min-w-11 px-2',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

type Props = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof variants> & { asChild?: boolean };

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { className, variant, size, asChild = false, ...props },
  ref,
) {
  const Component = asChild ? Slot : 'button';
  return (
    <Component
      ref={ref}
      className={cn(variants({ variant, size }), className)}
      {...props}
    />
  );
});

