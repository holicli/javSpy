$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public static class HidVendor
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

    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern IntPtr CreateFile(string FileName, uint DesiredAccess, uint ShareMode, IntPtr SecurityAttributes, uint CreationDisposition, uint FlagsAndAttributes, IntPtr TemplateFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr CreateEvent(IntPtr lpEventAttributes, bool bManualReset, bool bInitialState, string lpName);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool ReadFile(IntPtr hFile, byte[] lpBuffer, int nNumberOfBytesToRead, out int lpNumberOfBytesRead, ref OVERLAPPED lpOverlapped);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CancelIo(IntPtr hFile);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool DeviceIoControl(IntPtr hDevice, uint dwIoControlCode, IntPtr lpInBuffer, int nInBufferSize, byte[] lpOutBuffer, int nOutBufferSize, out int lpBytesReturned, IntPtr lpOverlapped);

    [DllImport("hid.dll", SetLastError = true)]
    public static extern bool HidD_SetNumInputBuffers(IntPtr HidDeviceObject, int NumberBuffers);

    public static string Hex(byte[] b, int n)
    {
        if (b == null || n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) { if (i > 0) sb.Append(' '); sb.Append(b[i].ToString("X2")); }
        return sb.ToString();
    }
}
'@

$path = "\\?\hid#vid_05ac&pid_024f&mi_02&col01#a&889e7da&0&0000#{4d1e55b2-f16f-11cf-88cb-001111000030}"

# 1) Report descriptor, read-only
$h = [HidVendor]::CreateFile($path, [uint32]2147483648, 3, [IntPtr]::Zero, 3, 0, [IntPtr]::Zero)
if ($h -eq [IntPtr]::Zero -or $h.ToInt64() -eq -1) {
    Write-Output ("OPEN_FAILED err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    exit 1
}

try {
    Write-Output "STEP2_OPENED"
    $desc = New-Object byte[] 4096
    $ret = 0
    $ok = [HidVendor]::DeviceIoControl($h, 0x000B000C, [IntPtr]::Zero, 0, $desc, $desc.Length, [ref]$ret, [IntPtr]::Zero)
    Write-Output ("REPORT_DESCRIPTOR ok=$ok len=$ret err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    if ($ok -and $ret -gt 0) {
        for ($i = 0; $i -lt $ret; $i += 16) {
            $n = [Math]::Min(16, $ret - $i)
            Write-Output ("  {0:X4}: {1}" -f $i, [HidVendor]::Hex($desc, $n) )
        }
    }
} finally { [void][HidVendor]::CloseHandle($h) }

# 2) Passive input-report capture, read-only, 6 s
Write-Output "STEP2_BEGIN"
$h2 = [HidVendor]::CreateFile($path, [uint32]2147483648, 3, [IntPtr]::Zero, 3, 0x40000000, [IntPtr]::Zero)
if ($h2 -eq [IntPtr]::Zero -or $h2.ToInt64() -eq -1) {
    Write-Output ("CAPTURE_OPEN_FAILED err=" + [Runtime.InteropServices.Marshal]::GetLastWin32Error())
    exit 1
}

try {
    Write-Output "STEP2_OPENED"
    [void][HidVendor]::HidD_SetNumInputBuffers($h2, 64)
    $evt = [HidVendor]::CreateEvent([IntPtr]::Zero, $true, $false, $null)
    $buf = New-Object byte[] 8
    $deadline = [DateTime]::UtcNow.AddSeconds(6)
    $count = 0
    while ([DateTime]::UtcNow -lt $deadline) {
        $ov = New-Object HidVendor+OVERLAPPED
        $ov.hEvent = $evt
        $read = 0
        $ok = [HidVendor]::ReadFile($h2, $buf, 8, [ref]$read, [ref]$ov)
        if (-not $ok) {
            $err = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
            if ($err -ne 997) { Write-Output ("READ_ERR " + $err); break }
            $w = [HidVendor]::WaitForSingleObject($evt, 500)
            if ($w -ne 0) { [void][HidVendor]::CancelIo($h2); continue }
        }
        if ($read -gt 0) {
            $count++
            Write-Output ("RX {0}: {1}" -f $count, [HidVendor]::Hex($buf, $read))
        }
    }
    Write-Output ("CAPTURE_DONE reports=$count")
} finally { [void][HidVendor]::CloseHandle($h2) }



