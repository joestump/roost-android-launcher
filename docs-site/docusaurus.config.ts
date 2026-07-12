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

// A single "source" link that matches the host THIS build is served from — GitHub Pages links to
// GitHub, Gitea/StumpCloud Pages links to Gitea. Resolved at build time from SITE_URL (each CI sets it),
// so no runtime host sniffing is needed.
const IS_GITHUB = SITE_URL.includes('github');
const SOURCE_URL = IS_GITHUB ? GITHUB_URL : GITEA_URL;
const SOURCE_LABEL = IS_GITHUB ? 'GitHub' : 'Gitea';
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

  // Expose the host-matched source link to client components (the landing hero button).
  customFields: {
    sourceUrl: SOURCE_URL,
    sourceLabel: SOURCE_LABEL,
  },

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
        {href: SOURCE_URL, label: SOURCE_LABEL, position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Project',
          items: [
            {label: SOURCE_LABEL, href: SOURCE_URL},
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
