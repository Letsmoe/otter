export function appendQueryParameter(url: string, name: string, value: string): string {
  let separator = "?";
  if (url.includes("?")) separator = "&";
  return `${url}${separator}${name}=${encodeURIComponent(value)}`;
}
