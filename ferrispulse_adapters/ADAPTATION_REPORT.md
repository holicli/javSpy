# 灵脉设备适配成果报告

**报告日期**：2026-11-08  
**适配对象**：HyperX Cloud II Wireless（Kingston，VID `0x0951` / PID `0x1718`）  
**交付文件**：`hyperx_cloud_ii_wireless.fp-driver.cs`（C# 5.0 动态驱动，Priority 300）  
**安全等级**：只读（不发送任何 Feature / Output 写报文）

---

## 0. 结论（TL;DR）

1. **你提供的指纹 `VID 0x05AC / PID 0x024F` 不是头戴式耳机。** 实机核验显示它是一个 **2.4G 无线键盘 + 触控板二合一接收器**，产品字符串为 `2.4G Wireless Receiver`，克隆了 Apple 的 VID/PID 以复用系统 HID 驱动。它没有音频接口，也没有任何 Feature Report。
2. **当前机器上真正的 2.4G 无线耳机是 `VID 0x0951 / PID 0x1718` 的 HyperX Cloud II Wireless**（Kingston）。它才是 FerrisPulse 目前未原生识别的耳机设备。
3. 我已按物理事实为 **HyperX Cloud II Wireless** 生成 `.fp-driver.cs`，并在本机实机验证：
   - 电量：**52%**
   - 电池电压：**约 3726 mV**
   - 充电状态：**未充电**
   - 连接状态：**已连接**
4. 驱动 **只以 `GENERIC_READ` 打开厂商集合，被动读取接收器主动上报的 `0x0B` 帧**；不发送任何查询或写入报文，不会触碰 EEPROM / Flash / RF 配对表。
5. 对于 `05AC:024F` 这个键盘/触控板接收器，**没有生成声明式 JSON 适配包**。原因见第 5 节：它没有可用的电量 Feature 通道，厂商端点 `0xFFB5` 在 8 秒只读监听中完全静默；在没有官方 WebHID 源码或真实回包样本的情况下，任何电量偏移/查询指令都只能是幻觉，且可能违反 FDAS 的 `sampleRxHex` 回放契约。

---

## 1. 物理事实核验

### 1.1 `05AC:024F` 到底是什么？

使用 `pnputil /enum-devices /instanceid ... /properties` 读取 Windows PnP 属性，得到：

| 属性 | 值 |
|---|---|
| `DEVPKEY_Device_BusReportedDeviceDesc` | `2.4G Wireless Receiver` |
| `DEVPKEY_Device_Manufacturer` | 空（无厂商字符串） |
| `DEVPKEY_Device_LocationInfo` | `Port_#0002.Hub_#0001`（USB 总线，不是蓝牙/雷雳） |
| 设备树 | `USB\VID_05AC&PID_024F` → 3 个 HID 子接口 `MI_00` / `MI_01` / `MI_02` |
| 音频接口 | **不存在**（没有任何 `MEDIA` / USB Audio Class 节点） |

HID 顶层集合共 7 个：

| # | UsagePage | Usage | InLen | OutLen | FeatureLen | PnP 描述 |
|---|---|---|---|---|---|---|
| 1 | `0x0001` | `0x0006` | 9 | 2 | 0 | HID Keyboard Device |
| 2 | `0x0001` | `0x0006` | 16 | 0 | 0 | HID Keyboard Device |
| 3 | `0x000C` | `0x0001` | 3 | 0 | 0 | HID-compliant consumer control |
| 4 | `0x0001` | `0x0080` | 2 | 0 | 0 | HID-compliant system controller |
| 5 | `0x000C` | `0x0001` | 2 | 0 | 0 | HID-compliant consumer control |
| 6 | `0xFFB5` | `0x0001` | 8 | 8 | 0 | HID-compliant vendor-defined device |
| 7 | `0x0001` | `0x0002` | 7 | 0 | 0 | HID-compliant mouse（触控板） |

这组拓扑是典型的 **无线键盘 + 触控板组合接收器**：两个键盘集合、一个鼠标/触控板集合、两个消费者控制集合、一个系统控制集合、一个厂商私有集合。它没有任何耳机/音频端点，因此不可能是头戴式耳机。

`05AC:024F` 在 Linux `systemd` hwdb 中对应 Apple `Aluminium Keyboard (ANSI)` / `Internal Keyboard/Trackpad (ANSI)`。该接收器显然克隆了 Apple 的 VID/PID（以及 Apple 风格的 HID 报表描述符），以在 Windows/macOS 上免驱工作，但 USB `iProduct` 暴露了它的真实身份：`2.4G Wireless Receiver`。

> 这也解释了探针指纹里“连接物理通道：蓝牙 / 雷雳接口”的来源：FerrisPulse 的 `AppleDeviceHelper` 对任何 `VID_05AC` 设备硬编码了 `Transport = "蓝牙 / 雷雳接口"`，并不是真的蓝牙连接。该 Helper 随后还会尝试 Feature Report `0x47` / `0xD1`，但本设备所有集合的 `FeatureReportByteLength` 均为 `0`，实机 `HidD_GetFeature` 全部返回失败（`ERROR_INVALID_FUNCTION`）。

### 1.2 只读监听结果

对厂商集合 `MI_02&Col01`（`0xFFB5/0x0001`，8 字节 IN/OUT）以 `GENERIC_READ` 打开，阻塞读取 8 秒：

```text
READING
（8 秒内无任何输入报文）
```

结论：该端点**不会主动上报**状态；若存在电量协议，必须由主机先发送某条未知命令。**在缺少官方 WebHID 源码或真实回包样本的前提下，不能猜测命令，更不能盲写。**

### 1.3 真正的耳机

同一台机器上，PnP 枚举到：

| 属性 | 值 |
|---|---|
| `VID:PID` | `0x0951:0x1718` |
| Bus reported name | `HyperX Cloud II Wireless` |
| Manufacturer | `Kingston` |
| Serial | `000000000001` |
| 音频接口 | USB Audio Class（`MI_00`） |
| HID 厂商集合 | `MI_03&Col03`：`UsagePage 0xFF13 / Usage 0x0001`，`InLen=62`，`OutLen=62`，`FeatureLen=0` |
| HID 消费者集合 | `MI_03&Col01`：`0x000C/0x0001`，`InLen=62`，`OutLen=62` |

这与 HyperX Cloud II Wireless 的公开协议描述完全一致：62 字节报文、厂商集合、Report ID `0x0B` 响应。

---

## 2. 协议逆向与实机样本

### 2.1 实机抓包

以 `GENERIC_READ` 只读打开 `0xFF13/0x0001`，连续读取 10 帧（未发送任何报文）：

```text
RX 1 : 0B 00 BB 02 00 0E 83 34 00 ...（后 54 字节为 00）
RX 2 : 0B 00 BB 03 00 00 00 00 00 ...
RX 3 : 0B 00 BB 02 00 0E 83 34 00 ...
RX 4 : 0B 00 BB 03 00 00 00 00 00 ...
RX 5 : 0B 00 BB 02 00 0E 83 34 00 ...
RX 6 : 0B 00 BB 03 00 00 00 00 00 ...
RX 7 : 0B 00 BB 02 00 0E 8E 34 00 ...
RX 8 : 0B 00 BB 03 00 00 00 00 00 ...
RX 9 : 0B 00 BB 02 00 0E 87 34 00 ...
RX 10: 0B 00 BB 03 00 00 00 00 00 ...
```

完整 62 字节样本（电量帧）：

```text
0B 00 BB 02 00 0E 83 34 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

完整 62 字节样本（充电帧）：

```text
0B 00 BB 03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

### 2.2 字段解析

响应统一格式：

```text
[0] 0x0B   Report ID
[1] 0x00   Fixed
[2] 0xBB   Fixed
[3] CMD    0x01=连接 / 0x02=电量 / 0x03=充电
[4..] DATA
```

| CMD | 含义 | 字段 |
|---|---|---|
| `0x02` | 电量 | `b[7]` = 0–100（本次 `0x34` = **52%**） |
| `0x02` | 电池电压（附加） | `b[5] * 256 + b[6]`（本次 `0x0E83` = **3715 mV**，后续帧 `0x0E8E` = 3726 mV） |
| `0x03` | 充电状态 | `b[4]`：`0`=未充电，`1`=充电中，`2`=已充满，`3`=充电错误（本次 `0x00` = **未充电**） |
| `0x01` | 连接状态 | `b[4]`：`1`/`4`=已连接，`2`=配对模式 |

### 2.3 交叉验证

该解析与开源项目 **HyperHeadset** 的 `src/devices/cloud_ii_wireless.rs` 以及 `old_cloud_2_doc/Headset_protocol.md` 完全一致：

- Cloud II Wireless 响应 Report ID `0x0B`，结构 `[11, 0, 187, cmd, ...]`；
- 电量命令 `0x02`，`response[7]` 为 0–100；
- 充电命令 `0x03`，`response[4]` 为 0/1/2/3；
- 设备会异步上报这些状态帧，因此无需发送查询命令。

参考：
- <https://github.com/LennardKittner/HyperHeadset>
- <https://github.com/LennardKittner/HyperHeadset/blob/main/old_cloud_2_doc/Headset_protocol.md>
- <https://github.com/LennardKittner/HyperHeadset/blob/main/src/devices/cloud_ii_wireless.rs>

---

## 3. 驱动设计

交付文件：`hyperx_cloud_ii_wireless.fp-driver.cs`

| 项目 | 设计 |
|---|---|
| 基类 | `RazerBatteryTray.HardwareDriverBase` |
| 优先级 | `Priority = 300`（`IsExternalPackage = true`） |
| `DriverId` | `Community:HyperXCloudIIWireless` |
| 匹配 | `VID 0x0951`，`PID 0x1718`（兼容同协议 `0x0B92`） |
| 端点选择 | 优先 `UsagePage 0xFF13 / Usage 0x0001`，`InputReportLength == 62`；回退到任意 62/62 集合 |
| 打开方式 | `HidTransport.OpenRead()` → `GENERIC_READ` |
| 读取 | `HidTransport.ReadReport()`，700 ms 总预算，单次 120 ms |
| 解析 | 仅接受 `0B 00 BB ...` 帧；CMD 2 取 `b[7]`，CMD 3 取 `b[4]` |
| 缓存 | `DriverStateStore.SaveBatteryState` / `TryLoadBatteryState`，键 `HyperX:0951:1718` |
| 节流 | 1.5 s，避免高频轮询占用 HID |
| 能力 | `SupportsBattery = true`；DPI / 回报率 / XY 独立全部 `false` |
| 充电未知 | 未收到 CMD 3 时 `IsChargingUnknown = true`，绝不伪造闪电图标 |

**安全边界**：驱动中没有 `WriteReport`、`SetFeature`、`HidD_SetOutputReport` 或任何写调用。即使设备协议支持侧音/自动关机等写命令，本驱动也刻意不实现，避免误写风险。

### 3.1 实机端到端验证

使用与 FerrisPulse 相同的 `HidTransport` 调用语义（`Enumerate` → `OpenRead` → `ReadReport`）编译并运行驱动：

```text
DriverId=Community:HyperXCloudIIWireless
Matches(0951,1718)=True
IsExternalPackage=True
Count=1
DeviceId=HyperX:0951:1718
Name=HyperX Cloud II Wireless
Category=Headset
Battery=52
Exact=52.0
Charging=False
ChargingUnknown=False
Connected=True
Sleeping=False
VoltageMv=3726
SupportsBattery=True
SupportsDpi=False
```

---

## 4. 安装与验证

### 4.1 导入

任选一种：

1. **文件导入**：把 `hyperx_cloud_ii_wireless.fp-driver.cs` 复制到  
   `%APPDATA%\FerrisPulse\adapters\`  
   （本机已存在该目录：`C:\Users\holic\AppData\Roaming\FerrisPulse\adapters`）
2. **界面导入**：打开 FerrisPulse →「导入扩展适配包」→ 选择该 `.fp-driver.cs` 文件 → 确认编译无错误。

### 4.2 验证

1. 确保 HyperX Cloud II Wireless 接收器插在 USB 口，耳机已开机。
2. 重启 FerrisPulse 或触发设备刷新。
3. 设备列表应出现 **HyperX Cloud II Wireless**（头戴式耳机图标），电量约 **52%**（以当前实机为准），不应出现虚假的充电闪电。
4. 若显示“未知/未连接”，请确认：
   - 耳机已开机且与接收器配对；
   - 没有其他软件（如 HyperX NGENUITY）独占厂商 HID 集合；
   - 接收器 PID 确为 `0x1718` 或 `0x0B92`。

### 4.3 卸载/回滚

删除 `%APPDATA%\FerrisPulse\adapters\hyperx_cloud_ii_wireless.fp-driver.cs`，重启 FerrisPulse 即可。内置驱动不受影响。

---

## 5. 为什么没有为 `05AC:024F` 生成 JSON 适配包

这不是“偷懒”，而是严格遵守 FDAS 的 **Proof by Sample Hex** 与硬件安全红线：

1. **分类错误**：`05AC:024F` 是键盘 + 触控板组合接收器，不是 Headset。把它声明为 Headset 会污染设备列表与 UI。
2. **没有电量通道**：
   - 7 个集合的 `FeatureReportByteLength` 全部为 `0`；
   - 厂商集合 `0xFFB5` 在 8 秒只读监听中没有任何主动上报；
   - 若存在电量查询，必须先发送未知命令，而未知写指令正是任务明令禁止的“盲测写指令”。
3. **没有真实 `sampleRxHex`**：FDAS 要求携带真实抓包样本并在加载前回放断言。没有样本就填写偏移，属于凭空捏造，无法通过回放，也可能误导后续写入。
4. **没有官方 WebHID 源码**：该设备是无品牌公版接收器，不存在 `hub.vgn.cn` / `hub.atk.pro` 这类可逆向的官方前端；无法提取原厂验证过的报文公式。
5. **Apple Feature 0x47/0xD1 不可用**：已实机验证 `HidD_GetFeature` 对 `0x47`、`0xD1` 均返回失败，不能套用 Apple Magic 的电量协议。

如果你确实想适配这个键盘/触控板接收器，正确的下一步是：

- 用官方 2.4G 键盘驱动（若有）抓取其 HID 报文；或
- 在只读监听的同时操作键盘/触控板，观察 `0xFFB5` 是否出现状态帧；或
- 提供该接收器配套键盘的型号/品牌，找到可核验的协议来源。

在拿到真实回包之前，**不应**向 `0xFFB5` 写入任何猜测命令。

---

## 6. 开源社区反哺

如果你验证通过，欢迎把本驱动与抓包样本回馈主线：

- GitHub Issues / PR：<https://github.com/Ferris-echo/FerrisPulse/issues>
- Gitee Issues / PR：<https://gitee.com/Ferris-echo/FerrisPulse/issues>
- 作者邮箱：<2949225840@qq.com> / <2949225840zpf@gmail.com>

建议在 Issue/PR 中附上：

1. `hyperx_cloud_ii_wireless.fp-driver.cs`
2. 本报告
3. 实机抓包（至少一帧 `0B 00 BB 02 ...` 与一帧 `0B 00 BB 03 ...`）
4. 设备 PnP 信息（`VID_0951&PID_1718`、`HyperX Cloud II Wireless`、`Kingston`）

---

## 7. 附录：本次核验使用的只读命令

```powershell
# 1. 查看 05AC:024F 的真实 USB 产品字符串
pnputil /enum-devices /instanceid "USB\VID_05AC&PID_024F\8&384b90af&0&2" /properties

# 2. 查看 0951:1718 的真实产品字符串
pnputil /enum-devices /instanceid "USB\VID_0951&PID_1718\000000000001" /properties

# 3. 只读打开 HyperX 厂商集合，读取 62 字节状态帧
#    端点: \\?\hid#vid_0951&pid_1718&mi_03&col03#...#{4d1e55b2-f16f-11cf-88cb-001111000030}
#    访问: GENERIC_READ (0x80000000)，不发送任何写报文
```

> 说明：本报告中的“52% / 3726 mV / 未充电”是 2026-11-08 实机采样值；电量会随使用变化，驱动会实时读取最新帧。
