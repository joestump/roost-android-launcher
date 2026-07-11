import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// ============================================================
// Roost — deployed to BOTH GitHub Pages and Gitea Pages.
// Both serve under the /roost-android-launcher/ subpath, so BASE_URL is shared;
// only the host (url) differs, set per-CI via the SITE_URL env var.
// ============================================================
const PROJECT_TITLE = 'Roost';
const PROJECT_TAGLINE = 'A dedicated device for your AI agent — a tiny, vendor-neutral Android launcher.';
const GITHUB_URL = 'https://github.com/joestump/roost-android-launcher';
const GITEA_URL = 'https://gitea.stump.rocks/joestump/roost-android-launcher';
const SITE_URL = process.env.SITE_URL || 'https://joestump.github.io';
const BASE_URL = '/roost-android-launcher/';
// ============================================================

const config: Config = {
  title: PROJECT_TITLE,
  tagline: PROJECT_TAGLINE,
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: SITE_URL,
  baseUrl: BASE_URL,

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  markdown: {
    format: 'detect',
  },

  themes: [
    [
      require.resolve('@easyops-cn/docusaurus-search-local'),
      {
        hashed: true,
        indexBlog: false,
        docsRouteBasePath: '/docs',
      },
    ],
  ],

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          path: 'docs',
          sidebarPath: './sidebars.ts',
          routeBasePath: '/docs',
          editUrl: `${GITEA_URL}/_edit/main/docs-site/`,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      defaultMode: 'dark',
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: PROJECT_TITLE,
      items: [
        {type: 'docSidebar', sidebarId: 'docs', position: 'left', label: 'Docs'},
        {href: GITHUB_URL, label: 'GitHub', position: 'right'},
        {href: GITEA_URL, label: 'Gitea', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: GITHUB_URL},
            {label: 'Gitea', href: GITEA_URL},
          ],
        },
      ],
      copyright: `Roost — MIT licensed. Not affiliated with any AI vendor.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['kotlin', 'bash', 'groovy'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
