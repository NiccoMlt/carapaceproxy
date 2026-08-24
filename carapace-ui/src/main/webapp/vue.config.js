const { defineConfig } = require('@vue/cli-service');

const DEV_MODE = process.env.NODE_ENV !== "production";

module.exports = defineConfig({
  publicPath: DEV_MODE ? '' : '/ui/',
  outputDir: 'ui',
})
