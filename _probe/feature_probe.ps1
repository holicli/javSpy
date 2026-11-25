$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class HidFeatureProbe
{
    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern IntPtr CreateFile(string FileName, uint DesiredAccess, uint ShareMode, IntPtr SecurityAttributes, uint CreationDisposition, uint FlagsAndAttributes, IntPtr TemplateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_GetFeature(IntPtr HidDeviceObject, byte[] ReportBuffer, int ReportBufferLength);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_GetAttributes(IntPtr HidDeviceObject, ref HIDD_ATTRIBUTES Attributes);

    [StructLayout(LayoutKind.Sequential)]
    public struct HIDD_ATTRIBUTES { public int Size; public ushort VendorID; public ushort ProductID; public ushort VersionNumber; }

    public static string Hex(byte[] b, int n)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) sb.Append(' '); sb.Append(b[i].ToString("X2")); }
        return sb.ToString();
    }
}
'@

$paths = @(
    @{ Name = 'vendor 0xFFB5'; Path = "\\?\hid#vid_05ac&pid_024f&mi_02&col01#a&889e7da&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}" },
    @{ Name = 'kbd 16B';      Path = "\\?\hid#vid_05ac&pid_024f&mi_01&col01#a&1bba0418&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}" },
    @{ Name = 'kbd 9B';       Path = "\\?\hid#vid_05ac&pid_024f&mi_00#a&2eea2056&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}" }
)

foreach ($p in $paths) {
    $h = [HidFeatureProbe]::CreateFile($p.Path, [uint32]1073741824, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero)
    if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) {
        Write-Output ("{0}: OPEN_FAILED {1}" -f $p.Name, [Runtime.InteropServices.Marshal]::GetLastWin32Error())
        continue
    }
    try {
        foreach ($rid in 0x47, 0xD1, 0x01, 0x02) {
            $buf = New-Object byte[] 65
            $buf[0] = [byte]$rid
            $ok = [HidFeatureProbe]::HidD_GetFeature($h, $buf, $buf.Length)
            $err = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
            if ($ok) {
                Write-Output ("{0}: GetFeature(0x{1:X2}) OK -> {2}" -f $p.Name, $rid, [HidFeatureProbe]::Hex($buf, 16))
            } else {
                Write-Output ("{0}: GetFeature(0x{1:X2}) FAIL err={2}" -f $p.Name, $rid, $err)
            }
        }
    } finally { [void][HidFeatureProbe]::CloseHandle($h) }
}
