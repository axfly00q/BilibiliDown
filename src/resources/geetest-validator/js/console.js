// Web 控制台脚本：仅做主题初始化 + i18n 文案应用
import { detectLang, loadDict, applyDom } from './i18n.js';
import { initTheme } from './theme.js';

(async function bootstrap() {
    initTheme('#theme-switch');
    try {
        const dict = await loadDict(detectLang());
        applyDom(dict);
    } catch (_) { /* keep keys */ }
})();
