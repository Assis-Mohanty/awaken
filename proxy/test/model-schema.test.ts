import { describe, expect, it } from "vitest";
import planContract from "../../contracts/path_plan.response.schema.json";
import weekContract from "../../contracts/path_week.response.schema.json";
import { toModelJsonSchema } from "../src/schemas/path.ts";

describe("toModelJsonSchema", () => {
  it.each([
    ["plan", planContract],
    ["week", weekContract],
  ])("does not ask the model for promptVersion (%s)", (_name, contract) => {
    const schema = toModelJsonSchema(contract as Record<string, unknown>);
    expect(Object.keys(schema.properties as object)).not.toContain("promptVersion");
    expect(schema.required as string[]).not.toContain("promptVersion");
    expect((schema.required as string[]).length).toBeGreaterThan(0);
  });
});
