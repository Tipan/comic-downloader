import {
    defineConfig,
    presetAttributify,
    presetIcons,
    presetTypography,
    presetUno,
    presetWebFonts,
    transformerDirectives,
    transformerVariantGroup
} from 'unocss'

export default defineConfig({
    shortcuts: {
        // 触屏优化：最小 44px 触控目标区
        'touch-target': 'min-w-11 min-h-11',
        // 图标按钮：44px 触控区 + 居中 + 圆角
        'icon-btn': 'min-w-11 min-h-11 flex items-center justify-center rounded-lg cursor-pointer',
    },
    theme: {
        colors: {
            // ...
        }
    },
    presets: [
        presetUno(),
        presetAttributify(),
        presetIcons(),
        presetTypography(),
        presetWebFonts({
            fonts: {
                // ...
            },
        }),
    ],
    transformers: [
        transformerDirectives(),
        transformerVariantGroup(),
    ],
})