$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class HyperXRead
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

$path = "\\?\hid#vid_0951&pid_1718&mi_03&col03#a&2a7b90c4&0&0002#{4d1e55b2-f16f-11cf-88cb-001111000030}"
$h = [HyperXRead]::CreateFile($path, [uint32]2147483648, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero)
if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) {
    Write-Output ("OPEN_FAILED " + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    exit 1
}
try {
    [void][HyperXRead]::HidD_SetNumInputBuffers($h, 64)
    $buf = New-Object byte[] 62
    for ($i = 1; $i -le 10; $i++) {
        $read = 0
        $ok = [HyperXRead]::ReadFile($h, $buf, 62, [ref]$read, [IntPtr]::Zero)
        $err = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
        if ($ok -and $read -gt 0) {
            Write-Output ("RX {0} n={1}: {2}" -f $i, $read, [HyperXRead]::Hex($buf, $read))
        } else {
            Write-Output ("READ_FAIL {0} err={1}" -f $i, $err)
            break
        }
    }
} finally { [void][HyperXRead]::CloseHandle($h) }
