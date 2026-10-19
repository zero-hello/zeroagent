import type { ComponentPropsWithRef } from "react";

export default function Logo(props: ComponentPropsWithRef<"svg">) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="256" height="256"
      fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round"
      role="img" aria-label="ZeroAgent" {...props}>
      <path d="M6 5H18L6 19H18" />
    </svg>
  );
}
