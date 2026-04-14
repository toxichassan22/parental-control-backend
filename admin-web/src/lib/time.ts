const formatter = new Intl.NumberFormat("ar-EG");

export function formatBytes(bytes: number): string {
  if (bytes <= 0) {
    return "0 B";
  }

  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${formatter.format(Number(value.toFixed(unitIndex === 0 ? 0 : 2)))} ${units[unitIndex]}`;
}

export function formatDate(epoch?: number): string {
  if (!epoch) {
    return "غير متاح";
  }

  return new Intl.DateTimeFormat("ar-EG", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(epoch));
}

