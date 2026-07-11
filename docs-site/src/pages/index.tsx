import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import styles from './index.module.css';

export default function Home(): JSX.Element {
  const {siteConfig} = useDocusaurusContext();

  const homeShot = useBaseUrl('img/home.png');
  const settingsShot = useBaseUrl('img/settings.png');
  const webappShot = useBaseUrl('img/webapp.png');

  const features = [
    {
      title: 'The launcher',
      shot: homeShot,
      href: '/docs/modes',
      desc: 'Boots into your agent. A robot mascot, a live greeting, and one aligned grid — the featured agent, your apps, and web apps.',
    },
    {
      title: 'Settings & theming',
      shot: settingsShot,
      href: '/docs/modes',
      desc: 'Name your agent, pick an accent tint, choose favorites, and paint a matching wallpaper — all framework-native, zero libraries.',
    },
    {
      title: 'Web apps',
      shot: webappShot,
      href: '/docs/modes',
      desc: 'Add a URL — a Homarr or Homepage dashboard — and it opens fullscreen like an app. Self-hosting, first-class.',
    },
  ];

  return (
    <Layout title="Roost" description={siteConfig.tagline}>
      <header className={styles.hero}>
        <div className={styles.heroInner}>
          <div className={styles.heroText}>
            <div className={styles.robot} aria-hidden="true">
              <span className={styles.antenna} />
              <span className={styles.head}>
                <span className={styles.eye} />
                <span className={styles.eye} />
              </span>
            </div>
            <h1 className={styles.heroTitle}>Roost</h1>
            <p className={styles.heroTagline}>{siteConfig.tagline}</p>
            <p className={styles.heroSub}>
              Turn a spare phone into a dedicated device for your AI agent — a little robot that lives on
              the phone, boots into your agent app, and keeps your tools one tap away.
            </p>
            <div className={styles.heroButtons}>
              <Link className={styles.btnPrimary} to="/docs/">Get started →</Link>
              <Link className={styles.btnGhost} to="https://github.com/joestump/roost-android-launcher">
                GitHub
              </Link>
            </div>
          </div>
          <div className={styles.heroShot}>
            <div className={styles.phone}>
              <img src={homeShot} alt="Roost home screen" />
            </div>
          </div>
        </div>
      </header>

      <main>
        <section className={styles.features}>
          {features.map((f) => (
            <Link key={f.title} to={f.href} className={styles.card}>
              <div className={styles.cardShot}>
                <img src={f.shot} alt={f.title} loading="lazy" />
              </div>
              <div className={styles.cardBody}>
                <h3>{f.title}</h3>
                <p>{f.desc}</p>
              </div>
            </Link>
          ))}
        </section>
      </main>
    </Layout>
  );
}
