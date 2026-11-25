using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32;

namespace RazerBatteryTray
{
    public enum DeviceCategory { Mouse, Keyboard, Headset, Earbuds, Gamepad, Dongle, Generic }

    public class DeviceCapabilities
    {
        public bool SupportsBattery { get; set; }
        public bool SupportsDpi { get; set; }
        public bool SupportsDpiStages { get; set; }
        public bool SupportsPollingRate { get; set; }
        public bool SupportsXyIndependence { get; set; }
        public bool SupportsHardwareDpiMonitor { get; set; }
        public string ThemeColorHex { get; set; }
        public string SensorDescription { get; set; }
    }

    public class MouseBatteryInfo
    {
        public string DeviceId { get; set; }
        public string DriverId { get; set; }
        public string Brand { get; set; }
        public DeviceCategory Category { get; set; }
        public string DeviceName { get; set; }
        public string Transport { get; set; }
        public bool IsCustom { get; set; }
        public bool IsDonglePresent { get; set; }
        public bool IsConnected { get; set; }
        public bool IsSleeping { get; set; }
        public bool IsCharging { get; set; }
        public bool IsChargingUnknown { get; set; }
        public int BatteryPercent { get; set; }
        public double ExactBatteryPercent { get; set; }
        public int BatteryVoltageMv { get; set; }
        public DateTime LastUpdated { get; set; }

        private DeviceCapabilities caps;
        public DeviceCapabilities Capabilities
        {
            get { if (caps == null) caps = new DeviceCapabilities(); return caps; }
            set { caps = value; }
        }
    }

    public abstract class HardwareDriverBase
    {
        public abstract string DriverId { get; }
        public abstract string Brand { get; }
        public virtual bool IsExternalPackage { get { return false; } }
        public virtual bool MatchesVidPid(int vid, int pid) { return false; }
        public virtual void PopulateCapabilities(MouseBatteryInfo dev) { }
        public virtual List<MouseBatteryInfo> QueryDevices() { return new List<MouseBatteryInfo>(); }
    }

    public class HidDeviceDescriptor
    {
        public string DevicePath;
        public string InstanceId;
        public int VendorId;
        public int ProductId;
        public int VersionNumber;
        public int UsagePage;
        public int Usage;
        public int InputReportLength;
        public int OutputReportLength;
        public int FeatureReportLength;
        public string ProductString;
        public string ManufacturerString;
    }

    public static class HidTransport
    {
        private const uint GenericRead = 0x80000000;
        private const uint ShareReadWrite = 0x00000003;
        private const uint OpenExisting = 3;
        private const uint FileFlagOverlapped = 0x40000000;
        private const int ErrorIoPending = 997;

        [StructLayout(LayoutKind.Sequential)]
        private struct HIDD_ATTRIBUTES
        {
            public int Size;
            public ushort VendorID;
            public ushort ProductID;
            public ushort VersionNumber;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct HIDP_CAPS
        {
            public ushort Usage;
            public ushort UsagePage;
            public ushort InputReportByteLength;
            public ushort OutputReportByteLength;
            public ushort FeatureReportByteLength;
            [MarshalAs(UnmanagedType.ByValArray, SizeConst = 17)]
            public ushort[] Reserved;
            public ushort NumberLinkCollectionNodes;
            public ushort NumberInputButtonCaps;
            public ushort NumberInputValueCaps;
            public ushort NumberInputDataIndices;
            public ushort NumberOutputButtonCaps;
            public ushort NumberOutputValueCaps;
            public ushort NumberOutputDataIndices;
            public ushort NumberFeatureButtonCaps;
            public ushort NumberFeatureValueCaps;
            public ushort NumberFeatureDataIndices;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct OVERLAPPED
        {
            public IntPtr Internal;
            public IntPtr InternalHigh;
            public uint Offset;
            public uint OffsetHigh;
            public IntPtr hEvent;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateFile(string name, uint access, uint share, IntPtr sec, uint disp, uint flags, IntPtr tmpl);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr h);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateEvent(IntPtr sec, bool manual, bool initial, string name);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr h, uint ms);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GetOverlappedResult(IntPtr h, ref OVERLAPPED ov, out uint transferred, bool wait);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool ReadFile(IntPtr h, byte[] buf, uint len, out uint read, ref OVERLAPPED ov);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CancelIo(IntPtr h);

        [DllImport("hid.dll", SetLastError = true)]
        private static extern bool HidD_GetAttributes(IntPtr h, ref HIDD_ATTRIBUTES attr);

        [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool HidD_GetProductString(IntPtr h, byte[] buf, uint len);

        [DllImport("hid.dll", SetLastError = true)]
        private static extern bool HidD_GetPreparsedData(IntPtr h, out IntPtr data);

        [DllImport("hid.dll", SetLastError = true)]
        private static extern bool HidD_FreePreparsedData(IntPtr data);

        [DllImport("hid.dll", SetLastError = true)]
        private static extern int HidP_GetCaps(IntPtr data, ref HIDP_CAPS caps);

        public static bool IsValidHandle(IntPtr h)
        {
            return h != IntPtr.Zero && h.ToInt64() != -1;
        }

        public static List<HidDeviceDescriptor> Enumerate(int vendorId, int productId)
        {
            List<HidDeviceDescriptor> list = new List<HidDeviceDescriptor>();
            try
            {
                using (RegistryKey root = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Enum\HID"))
                {
                    if (root == null) return list;
                    string[] tops = root.GetSubKeyNames();
                    for (int i = 0; i < tops.Length; i++)
                    {
                        string top = tops[i];
                        if (vendorId != 0 && top.IndexOf("VID_" + vendorId.ToString("X4"), StringComparison.OrdinalIgnoreCase) < 0) continue;
                        using (RegistryKey tk = root.OpenSubKey(top))
                        {
                            if (tk == null) continue;
                            string[] children = tk.GetSubKeyNames();
                            for (int j = 0; j < children.Length; j++)
                            {
                                string path = @"\\?\hid#" + top.ToLowerInvariant() + "#" + children[j].ToLowerInvariant() + "#{4d1e55b2-f16f-11cf-88cb-001111000030}";
                                IntPtr h = CreateFile(path, 0, ShareReadWrite, IntPtr.Zero, OpenExisting, 0, IntPtr.Zero);
                                if (!IsValidHandle(h)) continue;
                                try
                                {
                                    HIDD_ATTRIBUTES attr = new HIDD_ATTRIBUTES();
                                    attr.Size = Marshal.SizeOf(typeof(HIDD_ATTRIBUTES));
                                    if (!HidD_GetAttributes(h, ref attr)) continue;
                                    if (vendorId != 0 && attr.VendorID != vendorId) continue;
                                    if (productId != 0 && attr.ProductID != productId) continue;

                                    HidDeviceDescriptor d = new HidDeviceDescriptor();
                                    d.DevicePath = path;
                                    d.InstanceId = top + "\\" + children[j];
                                    d.VendorId = attr.VendorID;
                                    d.ProductId = attr.ProductID;
                                    d.VersionNumber = attr.VersionNumber;

                                    byte[] sb = new byte[512];
                                    if (HidD_GetProductString(h, sb, (uint)sb.Length))
                                    {
                                        int len = 0;
                                        while (len + 1 < sb.Length && !(sb[len] == 0 && sb[len + 1] == 0)) len += 2;
                                        d.ProductString = Encoding.Unicode.GetString(sb, 0, len).Trim();
                                    }

                                    HIDP_CAPS caps = new HIDP_CAPS();
                                    caps.Reserved = new ushort[17];
                                    IntPtr pd;
                                    if (HidD_GetPreparsedData(h, out pd))
                                    {
                                        HidP_GetCaps(pd, ref caps);
                                        HidD_FreePreparsedData(pd);
                                    }
                                    d.UsagePage = caps.UsagePage;
                                    d.Usage = caps.Usage;
                                    d.InputReportLength = caps.InputReportByteLength;
                                    d.OutputReportLength = caps.OutputReportByteLength;
                                    d.FeatureReportLength = caps.FeatureReportByteLength;
                                    list.Add(d);
                                }
                                finally { CloseHandle(h); }
                            }
                        }
                    }
                }
            }
            catch { }
            return list;
        }

        public static IntPtr OpenRead(string path)
        {
            return CreateFile(path, GenericRead, ShareReadWrite, IntPtr.Zero, OpenExisting, FileFlagOverlapped, IntPtr.Zero);
        }

        public static bool ReadReport(IntPtr h, int length, int timeoutMs, out byte[] data)
        {
            data = null;
            if (!IsValidHandle(h) || length <= 0) return false;
            IntPtr evt = CreateEvent(IntPtr.Zero, true, false, null);
            if (evt == IntPtr.Zero) return false;
            OVERLAPPED ov = new OVERLAPPED();
            GCHandle pinned = default(GCHandle);
            ov.hEvent = evt;
            try
            {
                byte[] buf = new byte[length];
                pinned = GCHandle.Alloc(buf, GCHandleType.Pinned);
                uint read;
                bool ok = ReadFile(h, buf, (uint)length, out read, ref ov);
                if (!ok && Marshal.GetLastWin32Error() == ErrorIoPending)
                {
                    uint w = WaitForSingleObject(evt, (uint)timeoutMs);
                    if (w != 0)
                    {
                        CancelIo(h);
                        GetOverlappedResult(h, ref ov, out read, true);
                        return false;
                    }
                    ok = GetOverlappedResult(h, ref ov, out read, false);
                }
                if (ok && read > 0)
                {
                    Array.Resize(ref buf, (int)read);
                    data = buf;
                    return true;
                }
                return false;
            }
            finally { if (pinned.IsAllocated) pinned.Free(); CloseHandle(evt); }
        }

        public static void Close(IntPtr h)
        {
            if (IsValidHandle(h)) CloseHandle(h);
        }
    }

    public static class DriverStateStore
    {
        public static void SaveBatteryState(string key, int pct, bool chg, string name) { }
        public static bool TryLoadBatteryState(string key, out int pct, out bool chg, out string name, out DateTime updated)
        {
            pct = -1;
            chg = false;
            name = "";
            updated = DateTime.MinValue;
            return false;
        }
    }

    public static class Program
    {
        public static void Main()
        {
            List<HidDeviceDescriptor> dbg = HidTransport.Enumerate(0x0951, 0);
            for (int di = 0; di < dbg.Count; di++)
            {
                HidDeviceDescriptor dd = dbg[di];
                Console.WriteLine("ENUM " + dd.InstanceId + " up=0x" + dd.UsagePage.ToString("X4") + " u=0x" + dd.Usage.ToString("X4") + " in=" + dd.InputReportLength + " out=" + dd.OutputReportLength);
            }
            HyperXCloudIIWirelessDriver drv = new HyperXCloudIIWirelessDriver();
            Console.WriteLine("DriverId=" + drv.DriverId);
            Console.WriteLine("Matches(0951,1718)=" + drv.MatchesVidPid(0x0951, 0x1718));
            Console.WriteLine("IsExternalPackage=" + drv.IsExternalPackage);

            List<MouseBatteryInfo> list = drv.QueryDevices();
            Console.WriteLine("Count=" + list.Count);
            for (int i = 0; i < list.Count; i++)
            {
                MouseBatteryInfo d = list[i];
                Console.WriteLine("DeviceId=" + d.DeviceId);
                Console.WriteLine("Name=" + d.DeviceName);
                Console.WriteLine("Category=" + d.Category);
                Console.WriteLine("Battery=" + d.BatteryPercent);
                Console.WriteLine("Exact=" + d.ExactBatteryPercent.ToString("0.0"));
                Console.WriteLine("Charging=" + d.IsCharging);
                Console.WriteLine("ChargingUnknown=" + d.IsChargingUnknown);
                Console.WriteLine("Connected=" + d.IsConnected);
                Console.WriteLine("Sleeping=" + d.IsSleeping);
                Console.WriteLine("VoltageMv=" + d.BatteryVoltageMv);
                Console.WriteLine("SupportsBattery=" + d.Capabilities.SupportsBattery);
                Console.WriteLine("SupportsDpi=" + d.Capabilities.SupportsDpi);
            }
        }
    }
}

