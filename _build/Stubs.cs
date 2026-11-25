using System;
using System.Collections.Generic;

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
        public int VendorId;
        public int ProductId;
        public int UsagePage;
        public int Usage;
        public int InputReportLength;
        public int OutputReportLength;
    }

    public static class HidTransport
    {
        public static List<HidDeviceDescriptor> Enumerate(int vid, int pid) { return new List<HidDeviceDescriptor>(); }
        public static IntPtr OpenRead(string path) { return IntPtr.Zero; }
        public static bool IsValidHandle(IntPtr h) { return false; }
        public static bool ReadReport(IntPtr h, int len, int timeout, out byte[] data) { data = null; return false; }
        public static void Close(IntPtr h) { }
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
}
