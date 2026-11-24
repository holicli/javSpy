$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class HidProbe
{
    [StructLayout(LayoutKind.Sequential)]
    public struct SP_DEVICE_INTERFACE_DATA
    {
        public int cbSize;
        public Guid InterfaceClassGuid;
        public int Flags;
        public IntPtr Reserved;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    public struct SP_DEVICE_INTERFACE_DETAIL_DATA
    {
        public int cbSize;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 1024)]
        public string DevicePath;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDD_ATTRIBUTES
    {
        public int Size;
        public ushort VendorID;
        public ushort ProductID;
        public ushort VersionNumber;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDP_CAPS
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

    [DllImport("setupapi.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern IntPtr SetupDiGetClassDevs(ref Guid ClassGuid, IntPtr Enumerator, IntPtr hwndParent, int Flags);

    [DllImport("setupapi.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern bool SetupDiEnumDeviceInterfaces(IntPtr DeviceInfoSet, IntPtr DeviceInfoData, ref Guid InterfaceClassGuid, int MemberIndex, ref SP_DEVICE_INTERFACE_DATA DeviceInterfaceData);

    [DllImport("setupapi.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr DeviceInfoSet, ref SP_DEVICE_INTERFACE_DATA DeviceInterfaceData, IntPtr DeviceInterfaceDetailData, int DeviceInterfaceDetailDataSize, out int RequiredSize, IntPtr DeviceInfoData);

    [DllImport("setupapi.dll", SetLastError = true)]
    public static extern bool SetupDiDestroyDeviceInfoList(IntPtr DeviceInfoSet);

    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern IntPtr CreateFile(string FileName, uint DesiredAccess, uint ShareMode, IntPtr SecurityAttributes, uint CreationDisposition, uint FlagsAndAttributes, IntPtr TemplateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_GetAttributes(IntPtr HidDeviceObject, ref HIDD_ATTRIBUTES Attributes);

    [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool HidD_GetProductString(IntPtr HidDeviceObject, StringBuilder Buffer, int BufferLength);

    [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool HidD_GetManufacturerString(IntPtr HidDeviceObject, StringBuilder Buffer, int BufferLength);

    [DllImport("hid.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool HidD_GetSerialNumberString(IntPtr HidDeviceObject, StringBuilder Buffer, int BufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_GetPreparsedData(IntPtr HidDeviceObject, out IntPtr PreparsedData);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_FreePreparsedData(IntPtr PreparsedData);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern int HidP_GetCaps(IntPtr PreparsedData, ref HIDP_CAPS Capabilities);
}
'@

$hidGuid = [Guid]'4d1e55b2-f16f-11cf-88cb-001111000030'
$DIGCF_PRESENT = 0x2
$DIGCF_DEVICEINTERFACE = 0x10
$INVALID = [IntPtr]::new(-1)

$results = @()
$set = [HidProbe]::SetupDiGetClassDevs([ref]$hidGuid, [IntPtr]::Zero, [IntPtr]::Zero, ($DIGCF_PRESENT -bor $DIGCF_DEVICEINTERFACE))
if ($set -eq $INVALID) { throw 'SetupDiGetClassDevs failed' }

try {
    $idx = 0
    while ($true) {
        $ifd = New-Object HidProbe+SP_DEVICE_INTERFACE_DATA
        $ifd.cbSize = [Runtime.InteropServices.Marshal]::SizeOf($ifd)
        if (-not [HidProbe]::SetupDiEnumDeviceInterfaces($set, [IntPtr]::Zero, [ref]$hidGuid, $idx, [ref]$ifd)) { break }

        $req = 0
        [void][HidProbe]::SetupDiGetDeviceInterfaceDetail($set, [ref]$ifd, [IntPtr]::Zero, 0, [ref]$req, [IntPtr]::Zero)
        $buf = [Runtime.InteropServices.Marshal]::AllocHGlobal($req)
        try {
            [Runtime.InteropServices.Marshal]::WriteInt32($buf, $(if ([IntPtr]::Size -eq 8) { 8 } else { 6 }))
            if (-not [HidProbe]::SetupDiGetDeviceInterfaceDetail($set, [ref]$ifd, $buf, $req, [ref]$req, [IntPtr]::Zero)) { $idx++; continue }
            $path = [Runtime.InteropServices.Marshal]::PtrToStringAuto([IntPtr]::Add($buf, $(if ([IntPtr]::Size -eq 8) { 8 } else { 6 })))
        } finally { [Runtime.InteropServices.Marshal]::FreeHGlobal($buf) }

        $h = [HidProbe]::CreateFile($path, 0, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero)
        if ($h -ne $INVALID) {
            try {
                $attr = New-Object HidProbe+HIDD_ATTRIBUTES
                $attr.Size = [Runtime.InteropServices.Marshal]::SizeOf($attr)
                [void][HidProbe]::HidD_GetAttributes($h, [ref]$attr)
                $prod = New-Object Text.StringBuilder 512
                [void][HidProbe]::HidD_GetProductString($h, $prod, 1024)
                $man = New-Object Text.StringBuilder 512
                [void][HidProbe]::HidD_GetManufacturerString($h, $man, 1024)
                $ser = New-Object Text.StringBuilder 512
                [void][HidProbe]::HidD_GetSerialNumberString($h, $ser, 1024)
                $caps = New-Object HidProbe+HIDP_CAPS
                $pd = [IntPtr]::Zero
                if ([HidProbe]::HidD_GetPreparsedData($h, [ref]$pd)) {
                    [void][HidProbe]::HidP_GetCaps($pd, [ref]$caps)
                    [void][HidProbe]::HidD_FreePreparsedData($pd)
                }
                $results += [pscustomobject]@{
                    Vid        = ('0x{0:X4}' -f $attr.VendorID)
                    Pid        = ('0x{0:X4}' -f $attr.ProductID)
                    Version    = ('0x{0:X4}' -f $attr.VersionNumber)
                    UsagePage  = ('0x{0:X4}' -f $caps.UsagePage)
                    Usage      = ('0x{0:X4}' -f $caps.Usage)
                    InLen      = $caps.InputReportByteLength
                    OutLen     = $caps.OutputReportByteLength
                    FeatLen    = $caps.FeatureReportByteLength
                    Product    = $prod.ToString()
                    Mfg        = $man.ToString()
                    Serial     = $ser.ToString()
                    Path       = $path
                }
            } finally { [void][HidProbe]::CloseHandle($h) }
        }
        $idx++
    }
} finally { [void][HidProbe]::SetupDiDestroyDeviceInfoList($set) }
$results | Sort-Object Vid, Pid, UsagePage, Usage | Format-Table -AutoSize | Out-String -Width 300

