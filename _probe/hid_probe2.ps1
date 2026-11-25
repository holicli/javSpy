$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class Hid2
{
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

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_GetFeature(IntPtr HidDeviceObject, byte[] ReportBuffer, int ReportBufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_SetNumInputBuffers(IntPtr HidDeviceObject, int NumberBuffers);

    public static IntPtr OpenPath(string path, uint access)
    {
        return CreateFile(path, access, 3u, IntPtr.Zero, 3u, 0u, IntPtr.Zero);
    }
}
'@

function Get-DevicePath([string]$top, [string]$child) {
    $t = $top.ToLowerInvariant()
    $c = $child.ToLowerInvariant()
    return "\\?\hid#$t#$c#{4d1e55b2-f16f-11cf-88cb-001111000030}"
}

$hidRoot = 'HKLM:\SYSTEM\CurrentControlSet\Enum\HID'
$targets = @()
Get-ChildItem $hidRoot -ErrorAction SilentlyContinue | Where-Object { $_.PSChildName -like '*VID_05AC&PID_024F*' } | ForEach-Object {
    $top = $_.PSChildName
    Get-ChildItem $_.PSPath -ErrorAction SilentlyContinue | ForEach-Object {
        $targets += [pscustomobject]@{ Top = $top; Child = $_.PSChildName }
    }
}

$results = @()
foreach ($t in $targets) {
    $path = Get-DevicePath $t.Top $t.Child
    $h = [Hid2]::OpenPath($path, 0)
    $ok = ($h -ne [IntPtr]::Zero -and $h.ToInt64() -ne -1)
    $row = [pscustomobject]@{
        Collection = $t.Top
        Instance   = $t.Child
        Open       = $ok
        Vid = ''; Pid = ''; Ver = ''; UsagePage = ''; Usage = ''
        InLen = ''; OutLen = ''; FeatLen = ''
        Product = ''; Mfg = ''; Serial = ''
        Err = ''
    }
    if ($ok) {
        try {
            $attr = New-Object Hid2+HIDD_ATTRIBUTES
            $attr.Size = [Runtime.InteropServices.Marshal]::SizeOf($attr)
            [void][Hid2]::HidD_GetAttributes($h, [ref]$attr)
            $row.Vid = ('0x{0:X4}' -f $attr.VendorID)
            $row.Pid = ('0x{0:X4}' -f $attr.ProductID)
            $row.Ver = ('0x{0:X4}' -f $attr.VersionNumber)
            $prod = New-Object Text.StringBuilder 512
            [void][Hid2]::HidD_GetProductString($h, $prod, 1024)
            $row.Product = $prod.ToString()
            $man = New-Object Text.StringBuilder 512
            [void][Hid2]::HidD_GetManufacturerString($h, $man, 1024)
            $row.Mfg = $man.ToString()
            $ser = New-Object Text.StringBuilder 512
            [void][Hid2]::HidD_GetSerialNumberString($h, $ser, 1024)
            $row.Serial = $ser.ToString()
            $caps = New-Object Hid2+HIDP_CAPS
            $pd = [IntPtr]::Zero
            if ([Hid2]::HidD_GetPreparsedData($h, [ref]$pd)) {
                [void][Hid2]::HidP_GetCaps($pd, [ref]$caps)
                [void][Hid2]::HidD_FreePreparsedData($pd)
            }
            $row.UsagePage = ('0x{0:X4}' -f $caps.UsagePage)
            $row.Usage = ('0x{0:X4}' -f $caps.Usage)
            $row.InLen = $caps.InputReportByteLength
            $row.OutLen = $caps.OutputReportByteLength
            $row.FeatLen = $caps.FeatureReportByteLength
        } finally { [void][Hid2]::CloseHandle($h) }
    } else {
        $row.Err = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
    }
    $results += $row
}

$results | ConvertTo-Json -Depth 3

