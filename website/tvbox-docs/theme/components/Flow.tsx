import type { ReactNode } from 'react';

/**
 * Lightweight flow diagram built from plain HTML and CSS.
 * No extra dependencies: works in Markdown-driven docs and stays
 * responsive, printable and accessible.
 */
export function Flow({
  children,
  direction = 'row',
}: {
  children: ReactNode;
  direction?: 'row' | 'column';
}) {
  return <div className={`tv-flow tv-flow--${direction}`}>{children}</div>;
}

export function FlowNode({
  title,
  children,
  tone = 'default',
}: {
  title?: string;
  children?: ReactNode;
  tone?: 'default' | 'accent' | 'muted';
}) {
  return (
    <div className={`tv-flow-node tv-flow-node--${tone}`}>
      {title ? <strong>{title}</strong> : null}
      {children ? <div className="tv-flow-node__body">{children}</div> : null}
    </div>
  );
}

export function Arrow({ label }: { label?: string }) {
  return (
    <div className="tv-flow-arrow" aria-hidden={label ? undefined : true}>
      <span>→</span>
      {label ? <em>{label}</em> : null}
    </div>
  );
}

/** Two-column layout used for before/after or A/B comparisons. */
export function FlowGrid({ children }: { children: ReactNode }) {
  return <div className="tv-flow-grid">{children}</div>;
}
