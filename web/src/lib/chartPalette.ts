export interface ChartPaletteSet {
  surface: string;
  text: string;
  textMuted: string;
  grid: string;
  axis: string;
  series: string[];
}

export const CHART_PALETTE: { light: ChartPaletteSet; dark: ChartPaletteSet } = {
  light: {
    surface: '#fcfcfb',
    text: '#0b0b0b',
    textMuted: '#898781',
    grid: '#e1e0d9',
    axis: '#c3c2b7',
    series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100'],
  },
  dark: {
    surface: '#1a1a19',
    text: '#ffffff',
    textMuted: '#898781',
    grid: '#2c2c2a',
    axis: '#383835',
    series: ['#3987e5', '#d95926', '#199e70', '#c98500'],
  },
};
