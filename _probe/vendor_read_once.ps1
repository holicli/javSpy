$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class HidReadOnce
{
    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern IntPtr CreateFile(string FileName, uint DesiredAccess, uint ShareMode, IntPtr SecurityAttributes, uint CreationDisposition, uint FlagsAndAttributes, IntPtr TemplateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool ReadFile(IntPtr hFile, byte[] lpBuffer, int nNumberOfBytesToRead, out int lpNumberOfBytesRead, IntPtr lpOverlapped);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_SetNumInputBuffers(IntPtr HidDeviceObject, int NumberBuffers);

    public static string Hex(byte[] b, int n)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) sb.Append(' '); sb.Append(b[i].ToString("X2")); }
        return sb.ToString();
    }
}
'@

$path = "\\?\hid#vid_05ac&pid_024f&mi_02&col01#a&889e7da&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}"
$h = [HidReadOnce]::CreateFile($path, [uint32]2147483648, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero)
if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) {
    Write-Output ("OPEN_FAILED " + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    exit 1
}
try {
    [void][HidReadOnce]::HidD_SetNumInputBuffers($h, 64)
    $buf = New-Object byte[] 8
    $read = 0
    Write-Output "READING"
    $ok = [HidReadOnce]::ReadFile($h, $buf, 8, [ref]$read, [IntPtr]::Zero)
    Write-Output ("READ ok=$ok n=$read err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    if ($read -gt 0) { Write-Output ("RX " + [HidReadOnce]::Hex($buf, $read)) }
} finally { [void][HidReadOnce]::CloseHandle($h) }
