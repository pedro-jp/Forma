export interface UserSummary {
  id: string;
  email: string;
  name: string;
  authProvider: 'APPLE' | 'GOOGLE' | 'EMAIL' | string;
  status: 'ACTIVE' | 'PAUSED' | string;
  accountTier?: 'FREE' | 'PRO' | string;
  pausedReason?: string | null;
  aiBlocked: boolean;
  dailyAiLimit?: number | null;
  todayAiRequests: number;
  totalAiRequests: number;
  foodsCount: number;
  workoutsCount: number;
  routinesCount: number;
  watersCount: number;
  weightsCount: number;
  analysesCount: number;
  goal?: string | null;
  currentWeight?: number | null;
  targetWeight?: number | null;
  targetCalorieIntake?: number | null;
  createdAt: string;
  lastLoginAt?: string | null;
  lastSyncedAt?: string | null;
  forceResetAt?: string | null;
}

export interface UserProfileDetail {
  goal?: string | null;
  targetCalorieIntake?: number | null;
  targetCalorieBurn?: number | null;
  targetSteps?: number | null;
  targetWeight?: number | null;
  targetProtein?: number | null;
  targetCarbohydrates?: number | null;
  targetFat?: number | null;
  targetWaterMilliliters?: number | null;
  heightMeters?: number | null;
  currentWeight?: number | null;
}

export interface UserFoodItem {
  id: string;
  name: string;
  brand?: string | null;
  mealType?: string | null;
  portionGrams?: number | null;
  calories?: number | null;
  protein?: number | null;
  carbohydrates?: number | null;
  fat?: number | null;
  consumedAt?: string | null;
}

export interface UserWorkoutItem {
  id: string;
  title: string;
  workoutType?: string | null;
  durationMinutes?: number | null;
  caloriesBurned?: number | null;
  notes?: string | null;
  workoutDate?: string | null;
}

export interface AiLogSummary {
  id: string;
  userId: string;
  endpoint: string;
  promptSummary: string;
  status: string;
  durationMs: number;
  createdAt: string;
}

export interface UserDetailResponse {
  user: UserSummary;
  profile: UserProfileDetail;
  recentFoods: UserFoodItem[];
  recentWorkouts: UserWorkoutItem[];
  recentAiLogs: AiLogSummary[];
}

export interface GeminiModelItem {
  id: string;
  displayName: string;
  description: string;
  isCurrent: boolean;
}

export interface AdminOverview {
  totalUsers: number;
  totalFoods: number;
  totalWorkouts: number;
  totalAiRequests: number;
  activeApiKeyMasked: string;
  hasValidApiKey: boolean;
  geminiModel: string;
  aiBlocked: boolean;
  recentUsers: UserSummary[];
  recentLogs: AiLogSummary[];
}

export interface WebSocketEvent<T = any> {
  type: 'CONNECTED' | 'AI_REQUEST' | 'USER_ACTIVITY' | 'SYNC_ACTIVITY' | 'KILL_SWITCH_CHANGED' | 'CONFIG_UPDATED';
  timestamp: string;
  data?: T;
  message?: string;
}
