$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class HyperXQuery
{
    [StructLayout(LayoutKind.Sequential)]
    public struct OVERLAPPED
    {
        public IntPtr Internal;
        public IntPtr InternalHigh;
        public uint Offset;
        public uint OffsetHigh;
        public IntPtr hEvent;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr CreateFile(string name, uint access, uint share, IntPtr sec, uint disp, uint flags, IntPtr tmpl);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr h);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr CreateEvent(IntPtr sec, bool manual, bool initial, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint WaitForSingleObject(IntPtr h, uint ms);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool GetOverlappedResult(IntPtr h, ref OVERLAPPED ov, out uint transferred, bool wait);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool ReadFile(IntPtr h, byte[] buf, uint len, out uint read, ref OVERLAPPED ov);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool WriteFile(IntPtr h, byte[] buf, uint len, out uint written, IntPtr ov);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CancelIo(IntPtr h);

    public static string Hex(byte[] b, int n)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) sb.Append(' '); sb.Append(b[i].ToString("X2")); }
        return sb.ToString();
    }
}
'@

$path = "\\?\hid#vid_0951&pid_1718&mi_03&col03#a&2a7b90c4&0&0002#{4d1e55b2-f16f-11cf-88cb-001111000030}"
$h = [HyperXQuery]::CreateFile($path, [uint32]3221225472, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero) # GENERIC_READ|GENERIC_WRITE
if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) { Write-Output ("OPEN_FAILED " + [Runtime.InteropServices.Marshal]::GetLastWin32Error()); exit 1 }

try {
    $tx = New-Object byte[] 62
    $tx[0]=0x06; $tx[2]=0x02; $tx[4]=0x9A; $tx[7]=0x68; $tx[8]=0x4A; $tx[9]=0x8E; $tx[10]=0x0A; $tx[14]=0xBB; $tx[15]=0x02
    $written = 0
    $ok = [HyperXQuery]::WriteFile($h, $tx, 62, [ref]$written, [IntPtr]::Zero)
    Write-Output ("TX ok=$ok written=$written err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    Write-Output ("TX " + [HyperXQuery]::Hex($tx, 20))
    [System.Threading.Thread]::Sleep(50)
    [void][HyperXQuery]::CloseHandle($h)
    $h = [HyperXQuery]::CreateFile($path, [uint32]3221225472, 3, [IntPtr]::Zero, 3, 0x40000000, [IntPtr]::Zero)
    if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) { Write-Output ("REOPEN_FAILED " + [Runtime.InteropServices.Marshal]::GetLastWin32Error()); exit 1 }

    $evt = [HyperXQuery]::CreateEvent([IntPtr]::Zero, $true, $false, $null)
    for ($i=1; $i -le 6; $i++) {
        $ov = New-Object HyperXQuery+OVERLAPPED
        $ov.hEvent = $evt
        $buf = New-Object byte[] 62
        $read = 0
        $rok = [HyperXQuery]::ReadFile($h, $buf, 62, [ref]$read, [ref]$ov)
        if (-not $rok -and [Runtime.InteropServices.Marshal]::GetLastWin32Error() -eq 997) {
            $w = [HyperXQuery]::WaitForSingleObject($evt, 300)
            if ($w -ne 0) { [HyperXQuery]::CancelIo($h); Write-Output "TIMEOUT $i"; break }
            $rok = [HyperXQuery]::GetOverlappedResult($h, [ref]$ov, [ref]$read, $false)
        }
        if ($rok -and $read -gt 0) { Write-Output ("RX $i n=$($read): " + [HyperXQuery]::Hex($buf, $read)) } else { Write-Output ("RXFAIL $i err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error()); break }
    }
} finally { [void][HyperXQuery]::CloseHandle($h) }
