const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');
const { withNativewind } = require('nativewind/metro');

const config = getDefaultConfig(__dirname);

/**
 * @tensorflow/tfjs-tflite ships dist/*.js files that do
 * `import * as tfliteWebAPIClient from '../tflite_web_api_client'`, but that
 * file is not published under dist/. It is published under wasm/. Every
 * bundler that follows the ESM entry point therefore fails to resolve it.
 *
 * Pointing the resolver at the real file is preferable to vendoring a patched
 * copy: the package stays a normal dependency, and the fix is one line that
 * disappears if upstream ever corrects the layout.
 */
const TFLITE_WEB_API_CLIENT = path.resolve(
  __dirname,
  'node_modules/@tensorflow/tfjs-tflite/wasm/tflite_web_api_client.js',
);

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName.endsWith('tflite_web_api_client')) {
    return { type: 'sourceFile', filePath: TFLITE_WEB_API_CLIENT };
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = withNativewind(config, { inlineRem: 16 });
