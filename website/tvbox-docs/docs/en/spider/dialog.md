---
description: Spider dialog fields and three-language examples: title, image, QR code, inputs, buttons and callback.
---

# Dialog

A spider can create a dialog from `action` to collect input or show a prompt. The fields below go in the `options` object of `dialog(options, callback)`; `callback` is the optional second argument.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `title` / `message` | string | none | Optional title and message. |
| `image` | string | none | Optional base64 image data URI, for example `data:image/png;base64,...`; the spider prepares the content. A callback can update it, and an empty string removes it. An open dialog reports decoding errors. |
| `qrcode` | string | none | Optional non-blank string encoded locally as a QR code; it does not have to be a URL. Encoding failures are reported. |
| `copyable` | boolean | `false` | Makes the message selectable and adds a copy-all button. Copying neither closes the dialog nor calls the callback. |
| `cancelable` | boolean | `true` | Allows cancellation with the Back key; `false` also blocks tapping outside. Destroying the page or calling cancel still cleans up the dialog. |
| `inputs` | object[] | none | Optional input fields. `name` is required and unique; `label` defaults to name; `value` is a string defaulting to an empty string, or a string array defaulting to an empty array for multiselect. |
| `inputs[].helperText` | string | none | Helper text below the field; hidden when omitted or empty. |
| `inputs[].type` | string | `text` | `text`, `phone`, `number` and `password` are single-line; `textarea` is multi-line; `select` is single choice and `multiselect` is a multiple-choice list. Format and business validation are the spider's responsibility. |
| `inputs[].options` | object[] | none | Required non-empty array for `select`／`multiselect`. `value` is a unique non-blank string; `label` defaults to value and cannot be blank. The label is displayed; single choice returns a string and multiselect returns an ordered string array. A non-empty initial value must exist in the options, and multiselect values cannot repeat. |
| `inputs[].enabled` | boolean | `true` | Allows interaction with the field; disabled fields skip built-in validation and still include their value in `values`. A callback can return an inputs array to update enabled by name. |
| `inputs[].required` | boolean | `false` | Requires a non-blank value; multiselect needs at least one choice. |
| `inputs[].minLength` / `maxLength` | integer | `0` / unlimited | Non-negative length limits for string fields, counted in Unicode characters. maxLength cannot be less than minLength; an omitted minLength is skipped. |
| `inputs[].minItems` / `maxItems` | integer | `0` / unlimited | Non-negative limits for multiselect fields, counted by selected items. maxItems cannot be less than minItems; an omitted minItems is skipped. |
| `buttons` | object[] | none | Optional one to three buttons mapped in order to positive, negative and neutral. id and label are required and non-blank, and ids must be unique. When omitted, OK (`ok`) and Cancel are shown. |
| `buttons[].close` | boolean | `true` | `false` keeps the dialog open and requires a callback. |
| `buttons[].enabled` | boolean | `true` | Allows interaction with the button. A callback can return a buttons array to update enabled by id. |
| `buttons[].validate` | string[] | all fields | Validates only the listed field names; an empty array skips validation. |
| `callback` | function | none | Optional second argument of `dialog`, not part of `options`. After validation it receives `{button, values}`, where button is the button id. Java／Python use a synchronous callback; QuickJS can return a Promise. Return `{message, qrcode, image, values, errors, inputs, buttons, close}` to update the dialog or its close behavior. |

## Three-language examples

Read user input; `dialog` returns `null`／`None` when canceled.

```java
JSONObject options = new JSONObject("""
{"title":"Input","inputs":[{"name":"text","label":"Content","required":true}]}
""");
JSONObject result = dialog(options);
if (result == null) return "{}";
String text = result.getJSONObject("values").getString("text");
```

```py
options = {
    "title": "Input",
    "inputs": [{"name": "text", "label": "Content", "required": True}],
}
result = self.dialog(options)
if result is None:
    return {}
text = result["values"]["text"]
```

```js
const result = await this.dialog({
  title: "Input",
  inputs: [{name: "text", label: "Content", required: true}]
});
if (result === null) return {};
const text = result.values.text;
```

## Related

- [Android UI](/spider/android-ui): `getActivity()` and `Runtime.toast()`.
- [Methods and timing](/spider/lifecycle): when `action` runs and how to return its result.
