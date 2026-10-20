/** wrangler.toml [[rules]] declares type = "Text" for Markdown files; see src/routes for the imports. */
declare module "*.md" {
  const content: string;
  export default content;
}
