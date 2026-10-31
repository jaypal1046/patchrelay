import { copyFileSync, mkdirSync, cpSync } from 'node:fs';
copyFileSync(new URL('../../LICENSE', import.meta.url), new URL('../LICENSE', import.meta.url));
mkdirSync(new URL('../gradle-plugin/src/main/', import.meta.url), { recursive: true });
for (const name of ['build.gradle', 'settings.gradle']) copyFileSync(new URL('../../gradle-plugin/' + name, import.meta.url), new URL('../gradle-plugin/' + name, import.meta.url));
cpSync(new URL('../../gradle-plugin/src/main/', import.meta.url), new URL('../gradle-plugin/src/main/', import.meta.url), { recursive: true });
