// ============================================================================
// 扩展包名称: hyperx_cloud_ii_wireless.fp-driver.cs
// 目标设备  : HyperX Cloud II Wireless (Kingston) 2.4G 无线耳机接收器
// 物理指纹  : VID 0x0951 / PID 0x1718 (兼容同协议 PID 0x0B92)
// 通信端点  : UsagePage 0xFF13 / Usage 0x0001, 62 字节 Interrupt IN/OUT
//
// 安全声明:
//   1. 本驱动只以 GENERIC_READ 打开厂商集合，被动读取接收器主动上报的 0x0B 帧；
//   2. 不发送任何 Feature / Output 报文，不触碰 EEPROM / Flash / RF 配对表；
//   3. 电量与充电状态均来自实机抓包与开源 HyperHeadset 协议文档双重交叉验证。
//
// 协议依据:
//   - 本机实测: 0B 00 BB 02 00 0E 83 34 ... -> 电量 byte[7]=0x34=52%
//   - 本机实测: 0B 00 BB 03 00 ...          -> 充电状态 byte[4]=0x00 (未充电)
//   - HyperHeadset (LennardKittner) Cloud II Wireless:
//       cmd 0x02 电量: response[7] = 0..100
//       cmd 0x03 充电: response[4] = 0 未充 / 1 充电 / 2 充满 / 3 错误
//
// C# 5.0 / .NET 4.0 兼容；严禁使用字符串插值、?.、nameof、out var。
// ============================================================================

using System;
using System.Collections.Generic;

namespace RazerBatteryTray
{
    public sealed class HyperXCloudIIWirelessDriver : HardwareDriverBase
    {
        private const int HyperXVid = 0x0951;
        private const int PidCloudIIWireless = 0x1718;
        private const int PidCloudIIWirelessAlt = 0x0B92;

        private const int PacketLength = 62;
        private const byte ResponseReportId = 0x0B;
        private const int ReadBudgetMs = 900;
        private const int SingleReadTimeoutMs = 150;
        private const int QueryThrottleMs = 1500;

        private const string DeviceKey = "HyperX:0951:1718";
        private const string DeviceDisplayName = "HyperX Cloud II Wireless";

        private static readonly object syncLock = new object();
        private static DateTime lastQueryUtc = DateTime.MinValue;
        private static int cachedBattery = -1;
        private static bool cachedCharging = false;
        private static bool cachedChargingKnown = false;
        private static bool cachedConnected = false;
        private static int cachedVoltageMv = 0;
        private static DateTime cachedUpdated = DateTime.MinValue;

        public override string DriverId
        {
            get { return "Community:HyperXCloudIIWireless"; }
        }

        public override string Brand
        {
            get { return "HyperX"; }
        }

        public override bool IsExternalPackage
        {
            get { return true; }
        }

        public override bool MatchesVidPid(int vid, int pid)
        {
            if (vid != HyperXVid) return false;
            return pid == PidCloudIIWireless || pid == PidCloudIIWirelessAlt;
        }

        public override void PopulateCapabilities(MouseBatteryInfo dev)
        {
            if (dev == null) return;
            if (dev.Capabilities == null) dev.Capabilities = new DeviceCapabilities();
            if (string.IsNullOrEmpty(dev.DriverId)) dev.DriverId = DriverId;

            dev.Capabilities.SupportsBattery = true;
            dev.Capabilities.SupportsDpi = false;
            dev.Capabilities.SupportsDpiStages = false;
            dev.Capabilities.SupportsPollingRate = false;
            dev.Capabilities.SupportsXyIndependence = false;
            dev.Capabilities.SupportsHardwareDpiMonitor = false;
            dev.Capabilities.ThemeColorHex = "#E2002B";
            dev.Capabilities.SensorDescription = "";
        }

        public override List<MouseBatteryInfo> QueryDevices()
        {
            lock (syncLock)
            {
                List<MouseBatteryInfo> result = new List<MouseBatteryInfo>();

                DateTime nowUtc = DateTime.UtcNow;
                if (cachedUpdated != DateTime.MinValue &&
                    (nowUtc - lastQueryUtc).TotalMilliseconds < QueryThrottleMs)
                {
                    result.Add(BuildDeviceInfo());
                    return result;
                }

                List<HidDeviceDescriptor> descriptors = HidTransport.Enumerate(HyperXVid, 0);
                HidDeviceDescriptor target = SelectTarget(descriptors);
                if (target == null)
                {
                    lastQueryUtc = DateTime.UtcNow;
                    return result;
                }

                bool gotBattery = false;
                bool gotCharging = false;
                bool gotConnection = false;
                bool connected = false;
                int battery = -1;
                int chargeStatus = -1;
                int voltageMv = 0;

                IntPtr handle = HidTransport.OpenRead(target.DevicePath);
                if (HidTransport.IsValidHandle(handle))
                {
                    try
                    {
                        long deadline = Environment.TickCount + ReadBudgetMs;
                        for (int i = 0; i < 20; i++)
                        {
                            int remain = (int)(deadline - Environment.TickCount);
                            if (remain <= 0) break;
                            int timeout = remain < SingleReadTimeoutMs ? remain : SingleReadTimeoutMs;

                            byte[] data;
                            if (!HidTransport.ReadReport(handle, PacketLength, timeout, out data)) continue;
                            if (data == null || data.Length < 8) continue;
                            if (data[0] != ResponseReportId) continue;
                            if (data[1] != 0x00 || data[2] != 0xBB) continue;

                            Console.WriteLine("FRAME " + BitConverter.ToString(data));
                            byte cmd = data[3];
                            if (cmd == 0x02)
                            {
                                int level = data[7];
                                if (level >= 0 && level <= 100)
                                {
                                    battery = level;
                                    gotBattery = true;
                                }

                                int mv = (data[5] << 8) | data[6];
                                if (mv >= 2500 && mv <= 4500) voltageMv = mv;
                            }
                            else if (cmd == 0x03)
                            {
                                chargeStatus = data[4];
                                gotCharging = true;
                            }
                            else if (cmd == 0x01)
                            {
                                byte status = data[4];
                                if (status == 1 || status == 4)
                                {
                                    connected = true;
                                    gotConnection = true;
                                }
                                else if (status == 2)
                                {
                                    connected = false;
                                    gotConnection = true;
                                }
                            }

                            if (gotBattery && gotCharging) break;
                        }
                    }
                    finally
                    {
                        HidTransport.Close(handle);
                    }
                }

                if (gotBattery)
                {
                    cachedBattery = battery;
                    cachedUpdated = DateTime.Now;
                }
                else
                {
                    LoadCachedState();
                }

                if (gotCharging)
                {
                    if (chargeStatus == 0)
                    {
                        cachedCharging = false;
                        cachedChargingKnown = true;
                    }
                    else if (chargeStatus == 1)
                    {
                        cachedCharging = true;
                        cachedChargingKnown = true;
                    }
                    else if (chargeStatus == 2)
                    {
                        cachedCharging = false;
                        cachedChargingKnown = true;
                        if (cachedBattery < 100) cachedBattery = 100;
                    }
                    else
                    {
                        cachedCharging = false;
                        cachedChargingKnown = false;
                    }
                }

                if (gotConnection)
                {
                    cachedConnected = connected;
                }
                else if (gotBattery || gotCharging)
                {
                    cachedConnected = true;
                }
                else
                {
                    cachedConnected = false;
                }

                if (voltageMv > 0) cachedVoltageMv = voltageMv;

                if (cachedBattery >= 0)
                {
                    DriverStateStore.SaveBatteryState(DeviceKey, cachedBattery, cachedCharging, DeviceDisplayName);
                }

                lastQueryUtc = DateTime.UtcNow;
                result.Add(BuildDeviceInfo());
                return result;
            }
        }

        private static HidDeviceDescriptor SelectTarget(List<HidDeviceDescriptor> descriptors)
        {
            if (descriptors == null || descriptors.Count == 0) return null;

            HidDeviceDescriptor relaxed = null;
            for (int i = 0; i < descriptors.Count; i++)
            {
                HidDeviceDescriptor d = descriptors[i];
                if (d == null) continue;
                if (d.ProductId != PidCloudIIWireless && d.ProductId != PidCloudIIWirelessAlt) continue;

                if (d.UsagePage == 0xFF13 && d.Usage == 0x0001 && d.InputReportLength == PacketLength)
                {
                    return d;
                }

                if (d.InputReportLength == PacketLength && d.OutputReportLength == PacketLength && relaxed == null)
                {
                    relaxed = d;
                }
            }

            return relaxed;
        }

        private static void LoadCachedState()
        {
            if (cachedBattery >= 0 && cachedUpdated != DateTime.MinValue) return;

            int savedBattery;
            bool savedCharging;
            string savedName;
            DateTime savedUpdated;
            if (DriverStateStore.TryLoadBatteryState(DeviceKey, out savedBattery, out savedCharging, out savedName, out savedUpdated))
            {
                if (cachedBattery < 0) cachedBattery = savedBattery;
                cachedCharging = savedCharging;
                cachedChargingKnown = true;
                if (cachedUpdated == DateTime.MinValue) cachedUpdated = savedUpdated;
            }
        }

        private static MouseBatteryInfo BuildDeviceInfo()
        {
            MouseBatteryInfo info = new MouseBatteryInfo();
            info.DeviceId = DeviceKey;
            info.DriverId = "Community:HyperXCloudIIWireless";
            info.Brand = "HyperX";
            info.Category = DeviceCategory.Headset;
            info.DeviceName = DeviceDisplayName;
            info.Transport = "2.4G 无线";
            info.IsCustom = false;
            info.IsDonglePresent = true;
            info.IsConnected = cachedConnected;
            info.IsSleeping = !cachedConnected;
            info.IsCharging = cachedCharging;
            info.IsChargingUnknown = !cachedChargingKnown;
            info.BatteryPercent = cachedBattery;
            info.ExactBatteryPercent = cachedBattery >= 0 ? (double)cachedBattery : 0.0;
            info.BatteryVoltageMv = cachedVoltageMv;
            info.LastUpdated = cachedUpdated == DateTime.MinValue ? DateTime.Now : cachedUpdated;

            info.Capabilities.SupportsBattery = true;
            info.Capabilities.SupportsDpi = false;
            info.Capabilities.SupportsDpiStages = false;
            info.Capabilities.SupportsPollingRate = false;
            info.Capabilities.SupportsXyIndependence = false;
            info.Capabilities.SupportsHardwareDpiMonitor = false;
            info.Capabilities.ThemeColorHex = "#E2002B";
            return info;
        }
    }
}
