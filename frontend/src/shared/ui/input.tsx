import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../lib/cn';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, type = 'text', ...props }, ref) {
    return (
      <input
        ref={ref}
        type={type}
        className={cn(
          'min-h-11 w-full rounded-xl border border-[#dce6eb] bg-[#f8fafb] px-3.5 text-sm text-[#182d38] outline-none placeholder:text-[#6f808a] focus-visible:outline-2 focus-visible:outline-[#416c82] disabled:cursor-not-allowed disabled:bg-[#edf1f4]',
          className,
        )}
        {...props}
      />
    );
  },
);

