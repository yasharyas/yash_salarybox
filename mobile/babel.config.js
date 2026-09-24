module.exports = function (api) {
  api.cache(true);

  return {
    presets: [['babel-preset-expo']],

    plugins: [
      [
        'module-resolver',
        {
          root: ['./'],

          alias: {
            '@': './',
            'tailwind.config': './tailwind.config.js',
          },
        },
      ],
      'react-native-worklets/plugin',
    ],

    overrides: [
      {
        // nativewind/babel (react-native-css's import rewrite) must not touch
        // react-native-web's own files. Its path check only matches "/" paths,
        // so on Linux/macOS it rewires react-native-web's internal imports to
        // react-native-css wrappers that require react-native-web's barrel
        // back, and the bundle crashes at startup. On Windows it never matched,
        // so this keeps every OS on the behaviour that works.
        // A function, not a RegExp: Expo loads this config once without a
        // filename (for its cache key) and Babel rejects RegExp patterns then.
        exclude: (filename) =>
          typeof filename === 'string' &&
          /[\\/]node_modules[\\/]react-native-web[\\/]/.test(filename),
        presets: ['nativewind/babel'],
      },
    ],
  };
};
