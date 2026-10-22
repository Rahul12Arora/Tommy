import Form from "@rjsf/core";
import { createPrecompiledValidator } from "@rjsf/validator-ajv8";
import { useMemo } from "react";
import validateFns from "../../generated/configValidator.js";
import { SECTIONS, schema, VERSION_KEYS } from "./sections.js";

// Compiled from the same schema at build time: no `new Function` in the browser, so the strict CSP holds (D-140)
const validator = createPrecompiledValidator(validateFns, schema);

/**
 * The whole draft in one RJSF form over the full schema; the open tab only decides which top-level sections are
 * visible (D-139). Hidden sections are still validated, so the error list at the top covers both tabs.
 */
export function ConfigForm({ section, draft, onChange, extraErrors, ref }) {
  const uiSchema = useMemo(() => {
    const hiddenKeys = [
      ...VERSION_KEYS,
      ...SECTIONS.filter((other) => other !== section).flatMap((other) => other.keys),
    ];
    return {
      "ui:submitButtonOptions": { norender: true }, // saving is the editor's button, not RJSF's
      ...Object.fromEntries(hiddenKeys.map((key) => [key, { "ui:widget": "hidden" }])),
    };
  }, [section]);

  return (
    <Form
      ref={ref}
      schema={schema}
      uiSchema={uiSchema}
      formData={draft}
      validator={validator}
      onChange={({ formData }) => onChange(formData)}
      extraErrors={extraErrors}
      liveValidate="onBlur"
      showErrorList="top"
      noHtml5Validate
    />
  );
}
