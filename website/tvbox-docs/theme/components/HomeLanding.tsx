'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useLang } from '@rspress/core/runtime';
import { Link } from '@rspress/core/theme';
import '../home-landing.css';

type ConfigItem = { name: string; url: string; color: string };
type LinkItem = { name: string; desc: string; url: string; icon: string };

const configs: ConfigItem[] = [
  { name: '饭太硬', url: 'http://www.饭太硬.cc/tv', color: '#28a9e0' },
  { name: '王二小', url: 'http://tvbox.xn--4kq62z5rby2qupq9ub.top/', color: '#c4e00f' },
  { name: '俊于', url: 'http://home.jundie.top:81/top98.json', color: '#5f3cab' },
  { name: 'R18接口(本地)', url: 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/R18.json', color: '#db1f76' },
  { name: 'undcover', url: 'https://raw.githubusercontent.com/UndCover/PyramidStore/main/py.json', color: '#FFA500' },
  { name: '香雅情', url: 'https://raw.githubusercontent.com/xyq254245/xyqonlinerule/main/XYQTVBox.json', color: '#1ba050' },
  { name: '高田流水js', url: 'https://raw.githubusercontent.com/gaotianliuyun/gao/master/js.json', color: '#1f3cfb' },
  { name: '南风', url: 'https://raw.githubusercontent.com/yoursmile66/TVBox/refs/heads/main/XC.json', color: '#7F8C8D' },
];

const links: LinkItem[] = [
  { name: '玩偶哥哥', desc: '网盘资源分享站，资源已对接，快来分享吧', url: 'https://www.wogg.net/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/WoGG.png' },
  { name: '加密接口解密', desc: '通通能解，可用于解密各种网络加密接口', url: 'http://www.饭太硬.net/jm/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/jm.jpeg' },
  { name: '太短', desc: '缩短链接，屌！也不是很短~', url: 'https://xiaomark.com/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/td.png' },
  { name: '太快', desc: 'GitHub 文件加速', url: 'https://gh.xxooo.cf/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/tk.png' },
  { name: '太能编', desc: '接口配置编辑器，支持加解密', url: 'http://z.qiqiv.cn/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/tnb.png' },
  { name: '直播大全', desc: 'CCTV直播大全', url: 'https://tv.cctv.com/live/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2024/11/06/a1631bcdf8e7e79cf31e19d44c1e3025.jpg' },
  { name: '秋水网', desc: '简单易用的导航网站', url: 'https://www.qiushui.vip/', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2025/03/01/f34a58938b3e2b08652fafcda58d77b2.th.jpg' },
  { name: '凯速直链文件存储', desc: '稳定速度快，个人私用接口托管足够了~', url: 'https://my.ksust.com/kstore.htm?aff=7212', icon: 'https://my.ksust.com/upload/avatar/000/1.png?1577675522' },
  { name: '爱达杂货铺', desc: '收集那些有用的东西', url: 'https://adzhp.xyz/', icon: 'https://adzhp.xyz/wp-content/uploads/2019/02/A.png' },
  { name: '直播源搜索引擎', desc: '国内外电视直播源搜索，m3u8、flv、rtsp……', url: 'http://www.foodieguide.com/iptvsearch/?s=', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/iptv.png' },
  { name: '直播源格式转换', desc: '直播源格式在线转换工具', url: 'https://sankei.mo/z.html', icon: 'https://gimg0.baidu.com/gimg/app=2001&n=0&g=0n&fmt=jpeg&src=s1.imagehub.cc/images/2023/07/01/zbzh.webp' },
];

const slogans = [
  '给时光以生命<br>给岁月以文明',
  '凡心所向，素履所往<br>生如逆旅，一苇以航',
  '纵有千古，横有八荒<br>前途似海，来日方长',
  '看尽天下A片<br>心中自然无码',
  '还是古代好混<br>割掉了就能当公务员',
  '一分钟有多长<br>是要看你蹲在厕所里面还是蹲在厕所外面',
  '人生有时就象在拉屎<br>明明已经很努力了可出来的只是一个屁',
  '不要以为全世界只有你一个在伤心<br>别人只是比你会掩饰',
  '愿你以渺小启程<br>以伟大结束<br>在星辰大海的征途中<br>遇到那个最想要的自己',
  '可以选择放弃<br>但不能放弃选择',
  '没有一个冬天不可逾越<br>没有一个春天不会来临',
];

function Starfield() {
  const ref = useRef<HTMLCanvasElement | null>(null);
  useEffect(() => {
    const canvas = ref.current;
    const parent = canvas?.parentElement;
    if (!canvas || !parent) return;
    const ctx = canvas.getContext('2d');
    const offscreen = document.createElement('canvas');
    const offCtx = offscreen.getContext('2d');
    if (!ctx || !offCtx) return;

    let raf = 0;
    let resizeTimer = 0;
    let frame = 0;

    const resize = () => {
      const width = parent.offsetWidth;
      const height = parent.offsetHeight;
      const size = Math.max(width, height);
      canvas.width = Math.max(1, width);
      canvas.height = Math.max(1, height);
      offscreen.width = Math.max(1, Math.floor(size * 2.6));
      offscreen.height = offscreen.width;

      offCtx.setTransform(1, 0, 0, 1, 0, 0);
      offCtx.fillStyle = 'rgba(21,21,21,1)';
      offCtx.fillRect(0, 0, offscreen.width, offscreen.height);
      offCtx.lineCap = 'round';

      for (let i = 20000; i--;) {
        const c = 120 + Math.floor(Math.random() * 136);
        const alpha = (30 + Math.random() * 70) / 100;
        offCtx.beginPath();
        offCtx.arc(
          Math.random() * offscreen.width,
          Math.random() * offscreen.height,
          1.2,
          0,
          Math.PI * 2,
          true,
        );
        offCtx.fillStyle = `rgba(${c},${c},${c},${alpha})`;
        offCtx.fill();
      }

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.fillStyle = 'rgba(21,21,21,1)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.lineCap = 'round';
      ctx.translate(canvas.width / 2, canvas.height / 2);
    };

    const draw = () => {
      ctx.drawImage(offscreen, -offscreen.width / 2, -offscreen.height / 2);
      if (++frame > 150 && frame % 8 === 0) {
        ctx.fillStyle = 'rgba(0,0,0,.04)';
        ctx.fillRect(-offscreen.width / 2, -offscreen.height / 2, offscreen.width, offscreen.height);
      }
      ctx.rotate((0.025 * Math.PI) / 180);
      raf = requestAnimationFrame(draw);
    };

    const onResize = () => {
      window.clearTimeout(resizeTimer);
      resizeTimer = window.setTimeout(resize, 150);
    };

    resize();
    draw();
    window.addEventListener('resize', onResize);
    return () => {
      cancelAnimationFrame(raf);
      window.clearTimeout(resizeTimer);
      window.removeEventListener('resize', onResize);
    };
  }, []);
  return <canvas ref={ref} className="landing-startrack" aria-hidden="true" />;
}

export default function HomeLanding() {
  const lang = useLang();
  const isEn = lang === 'en';
  const [slogan, setSlogan] = useState(slogans[0]);
  const [toast, setToast] = useState('');

  useEffect(() => {
    setSlogan(slogans[Math.floor(Math.random() * slogans.length)]);
  }, []);

  useEffect(() => {
    const onScroll = () => {
      const background = document.querySelector('.landing-background');
      background?.classList.toggle('is-fixed', window.scrollY > 0.6 * window.innerHeight);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const copy = useCallback(async (text: string, name: string) => {
    const message = isEn ? `${name} copied. Paste it into your TVbox launcher.` : `${name} 已复制，请前往壳设置里粘贴接口`;
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      const input = document.createElement('textarea');
      input.value = text;
      document.body.appendChild(input);
      input.select();
      document.execCommand('copy');
      input.remove();
    }
    setToast(message);
    window.setTimeout(() => setToast(''), 2200);
  }, [isEn]);

  return (
    <main className="home-landing" lang={isEn ? 'en' : 'zh-CN'}>
      <div className="landing-background" aria-hidden="true">
        <Starfield />
        <div className="landing-cover" />
      </div>

      <nav className="landing-nav" aria-label="页面导航">
        <a href="#config">{isEn ? 'Configs' : '接口'}</a>
        <a href="#resources">{isEn ? 'Resources' : '资源'}</a>
        <Link href={isEn ? '/guide/start/introduction' : '/guide/start/introduction'}>{isEn ? 'Docs' : '文档'}</Link>
        <a href="https://github.com/kukuqi666/TVboxOSC">GitHub</a>
        <a href={isEn ? '/index.html' : '/en/index.html'} lang={isEn ? 'zh' : 'en'}>
          {isEn ? '中文' : 'English'}
        </a>
      </nav>

      <section className="landing-section landing-intro">
        <div className="landing-container">
          <div className="landing-hello">
            <h1 dangerouslySetInnerHTML={{ __html: isEn ? 'Explore freely<br>Enjoy every screen' : slogan }} />
            <h2><span className="landing-circle"><i /><i /><i /></span>tvbox</h2>
          </div>
        </div>
      </section>

      <section className="landing-section landing-find" id="config">
        <div className="landing-container">
          <h2 className="landing-title">{isEn ? 'Configs' : '空壳'}<span>{isEn ? 'Click to copy' : '接口（点击复制）'}</span></h2>
          <div className="landing-grid landing-config-grid">
            {configs.map((item) => (
              <button type="button" className="landing-config-item" key={item.name} onClick={() => copy(item.url, item.name)}>
                <span className="landing-config-bg" style={{ backgroundColor: item.color }} />
                <span className="landing-config-inner">{item.name}</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="landing-section landing-gate" id="resources">
        <div className="landing-container">
          <h2 className="landing-title">{isEn ? 'Open TVbox' : '开源空壳'}<span>{isEn ? 'Related free resources' : '相关免费资源推荐'}</span></h2>
          <div className="landing-grid landing-link-grid">
            {links.map((item) => (
              <a className="landing-link-item" key={item.name} href={item.url} target="_blank" rel="noreferrer">
                <span className="landing-avatar"><img src={item.icon} alt="" loading="lazy" /></span>
                <span className="landing-link-inner"><strong>{item.name}</strong><small>{item.desc}</small></span>
              </a>
            ))}
          </div>
        </div>
      </section>

      <footer className="landing-section landing-footer">
        <div className="landing-container">
          <h4>© 2026 Powered by tvbox</h4>
          <a target="_blank" rel="noreferrer" href="https://github.com/kukuqi666/TVboxOSC">{isEn ? 'Hosted on' : '本网站托管于'} <mark>Github</mark></a>
        </div>
      </footer>

      <div className={`landing-toast${toast ? ' is-visible' : ''}`} role="status">{toast}</div>
    </main>
  );
}
