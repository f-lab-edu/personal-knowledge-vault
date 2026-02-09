import { Toaster as Sonner } from "sonner";

const Toaster = (props) => {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      style={{
        "--normal-bg": "var(--color-background)",
        "--normal-text": "var(--color-foreground)",
        "--normal-border": "var(--color-border)",
      }}
      {...props}
    />
  );
};

export { Toaster };
