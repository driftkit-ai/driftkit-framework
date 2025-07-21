const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,

  // Базовый путь для всех ресурсов Vue приложения
  publicPath: '/prompt-engineering/',

  // Директория для сборки (по умолчанию 'dist')
  outputDir: 'dist',

  // Директория для статических ресурсов внутри outputDir
  assetsDir: 'static',

  // Настройка прокси для разработки (опционально, если вы всё ещё используете прокси)
  devServer: {
    proxy: {
      '/data': {
        target: 'http://localhost:8085', // Адрес вашего Spring Boot бэкенда
        changeOrigin: true,
        pathRewrite: { '^/data': '' },
        secure: false, // Если используете HTTPS на бэкенде, установите true
      },
    },
    // This enables SPA routing with history mode
    historyApiFallback: {
      rewrites: [
        { from: /\/prompt-engineering\//, to: '/prompt-engineering/index.html' },
        { from: /./, to: '/index.html' }
      ]
    },
  },
});