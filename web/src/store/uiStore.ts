import { create } from 'zustand';

interface UiState {
  railExpanded: boolean;
  toggleRail: () => void;
  moreSheetOpen: boolean;
  setMoreSheetOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>((set, get) => ({
  railExpanded: false,
  toggleRail: () => set({ railExpanded: !get().railExpanded }),
  moreSheetOpen: false,
  setMoreSheetOpen: (open) => set({ moreSheetOpen: open }),
}));
