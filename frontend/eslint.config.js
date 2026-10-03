import js from "@eslint/js";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";

// Core no-unused-vars can't see JSX usage (`<Foo />` never reads the `Foo` binding in its eyes), so every
// imported component was reported unused. This is the one rule we'd need from eslint-plugin-react
// (`react/jsx-uses-vars`): mark the identifier behind each JSX element name as used.
const jsxUsesVars = {
  rules: {
    "jsx-uses-vars": {
      meta: { type: "problem", schema: [] },
      create(context) {
        return {
          JSXOpeningElement(node) {
            let name = node.name;
            while (name.type === "JSXMemberExpression") {
              name = name.object;
            }
            if (name.type === "JSXIdentifier") {
              context.sourceCode.markVariableAsUsed(name.name, node);
            }
          },
        };
      },
    },
  },
};

export default [
  { ignores: ["dist/**", "node_modules/**"] },
  {
    files: ["**/*.{js,jsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      parserOptions: { ecmaFeatures: { jsx: true } },
      globals: { ...globals.browser, ...globals.node },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      local: jsxUsesVars,
    },
    rules: {
      ...js.configs.recommended.rules,
      "local/jsx-uses-vars": "error",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "no-unused-vars": ["warn", { argsIgnorePattern: "^_" }],
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
    },
  },
];
