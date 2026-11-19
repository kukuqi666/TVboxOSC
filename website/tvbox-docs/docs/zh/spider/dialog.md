---
description: 爬虫 dialog 的栏位与三语言范例：标题、图片、二维码、输入框、按钮与回呼。
---

# Dialog 对话窗

爬虫可从 `action` 建立对话窗，收集输入或显示提示。以下栏位用于 `dialog(options, callback)` 的 `options`；`callback` 是选填第二参数。

## 栏位

| 栏位 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `title` / `message` | string | 无 | 选填标题与说明。 |
| `image` | string | 无 | 选填 base64 图片 data URI，例如 `data:image/png;base64,...`；由爬虫准备内容。callback 可更新，空字串移除；解码失败时，尚未结束的 dialog 会回报错误。 |
| `qrcode` | string | 无 | 选填非空白字串，在本机产生 QR Code；内容不必是网址，无法编码时回报错误。 |
| `copyable` | boolean | `false` | 可选取 message，并提供复制全文按钮；复制不关窗，也不呼叫 callback。 |
| `cancelable` | boolean | `true` | 允许返回键取消；`false` 时也阻止点外侧取消。页面销毁及呼叫取消仍会清理视窗。 |
| `inputs` | object[] | 无 | 选填输入栏位；`name` 必填且不可重复，`label` 预设为 name；`value` 为字串，预设空字串；多选为字串阵列，预设空阵列。 |
| `inputs[].helperText` | string | 无 | 栏位下方的说明文字，省略或空字串时隐藏。 |
| `inputs[].type` | string | `text` | `text`、`phone`、`number`、`password` 为单行，`textarea` 为多行，`select` 为单选，`multiselect` 为多选清单；格式与业务验证由爬虫负责。 |
| `inputs[].options` | object[] | 无 | `select`／`multiselect` 必填非空阵列；`value` 为非空白且唯一的字串，`label` 预设为 value 且不可空白。显示 label，单选回传字串，多选依选项顺序回传字串阵列；非空初始值须存在于选项，多选不可重复。 |
| `inputs[].enabled` | boolean | `true` | 允许操作栏位；停用时略过内建验证，values 仍包含其值。callback 可回传 inputs 阵列，依 name 更新 enabled。 |
| `inputs[].required` | boolean | `false` | 不可为空或全空白，多选至少选一项。 |
| `inputs[].minLength` / `maxLength` | integer | `0` / 不限制 | 字串栏位的非负整数限制，以 Unicode 字元计算；maxLength 不可小于 minLength。留空时略过 minLength。 |
| `inputs[].minItems` / `maxItems` | integer | `0` / 不限制 | 多选栏位的非负整数限制，以已选数量计算；maxItems 不可小于 minItems。留空时略过 minItems。 |
| `buttons` | object[] | 无 | 选填 1～3 个按钮，依序对应 positive、negative、neutral；id 与 label 必填且不可为空，id 不可重复。省略时提供确定（ok）与取消。 |
| `buttons[].close` | boolean | `true` | `false` 时保留视窗，必须提供 callback。 |
| `buttons[].enabled` | boolean | `true` | 允许操作按钮；callback 可回传 buttons 阵列，依 id 更新 enabled。 |
| `buttons[].validate` | string[] | 全部栏位 | 验证指定的栏位 name；空阵列略过验证。 |
| `callback` | function | 无 | dialog 的选填第二参数，不放在 options 内。验证通过后接收 `{button, values}`，button 为按钮 id。Java／Python 使用同步回呼，QuickJS 可回传 Promise。回传 `{message, qrcode, image, values, errors, inputs, buttons, close}` 更新内容或关窗设定。 |

## 三语言范例

取得使用者输入；取消时 `dialog` 回传 `null`／`None`。

```java
JSONObject options = new JSONObject("""
{"title":"输入内容","inputs":[{"name":"text","label":"内容","required":true}]}
""");
JSONObject result = dialog(options);
if (result == null) return "{}";
String text = result.getJSONObject("values").getString("text");
```

```py
options = {
    "title": "输入内容",
    "inputs": [{"name": "text", "label": "内容", "required": True}],
}
result = self.dialog(options)
if result is None:
    return {}
text = result["values"]["text"]
```

```js
const result = await this.dialog({
  title: "输入内容",
  inputs: [{name: "text", label: "内容", required: true}]
});
if (result === null) return {};
const text = result.values.text;
```

## 相关章节

- [Android UI](/spider/android-ui)：`getActivity()` 与 `Runtime.toast()`。
- [自订操作](/spider/lifecycle)：`action` 的呼叫时机与回传格式。
