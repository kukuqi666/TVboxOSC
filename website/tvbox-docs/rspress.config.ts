import * as path from 'node:path';
import { defineConfig } from '@rspress/core';

// 正式网址由 GitHub Actions 的 configure-pages 通过 SITE_URL 提供，
// 构建时可用 SITE_URL 覆写，例如 https://kukuqi666.github.io/TVboxOSC/
const siteUrl = process.env.SITE_URL ? new URL(process.env.SITE_URL) : undefined;
const base = siteUrl ? siteUrl.pathname.replace(/\/$/, '') || '/' : '/';

const zh = {
  label: '简体中文',
  title: 'TVboxOSC 使用与开发指南',
  description: 'TVboxOSC 的爬虫介接、配置字典、本地 API 与 App 功能指南。',
};

const en = {
  label: 'English',
  title: 'TVboxOSC Guide',
  description:
    'Spider integration, configuration reference, local API and App features for TVboxOSC.',
};

export default defineConfig({
  root: path.join(__dirname, 'docs'),
  base,
  title: zh.title,
  description: zh.description,
  lang: 'zh',
  icon: '/logo.svg',
  logo: '/logo.svg',
  locales: [
    { lang: 'zh', label: zh.label, title: zh.title, description: zh.description },
    { lang: 'en', label: en.label, title: en.title, description: en.description },
  ],
  markdown: {
    checkDeadLinks: true,
  },
  themeConfig: {
    socialLinks: [
      { icon: 'github', mode: 'link', content: 'https://github.com/kukuqi666/TVboxOSC' },
    ],
  },
});
