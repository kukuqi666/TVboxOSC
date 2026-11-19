'use client';

import { useLang, useSite } from '@rspress/core/runtime';
import '../legacy-landing.css';

export default function LegacyLanding() {
  const { site } = useSite();
  const lang = useLang();
  const base = site.base === '/' ? '' : site.base.replace(/\/$/, '');
  const docsPath = `${base}/${lang === 'en' ? 'en/' : ''}guide/start/introduction`;

  return (
    <div className="legacy-landing">
      <iframe className="legacy-landing__frame" src={`${base}/legacy/index.html`} title="TVbox" />
      <a className="legacy-landing__docs" href={docsPath}>
        {lang === 'en' ? 'View Developer Docs' : '查看开发者文档'}
      </a>
    </div>
  );
}
