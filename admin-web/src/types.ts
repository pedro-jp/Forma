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

export interface AdminBootstrapResponse {
  username: string;
  overview: AdminOverview;
  billing: BillingDashboardData | null;
  auditLogs: AdminAuditItem[];
  incidents: SecurityIncidentItem[];
  serverTimestampUtc: string;
}

export interface WebSocketEvent<T = any> {
  type:
    | 'CONNECTED'
    | 'AI_REQUEST'
    | 'USER_ACTIVITY'
    | 'SYNC_ACTIVITY'
    | 'KILL_SWITCH_CHANGED'
    | 'CONFIG_UPDATED'
    | 'BILLING_UPDATED'
    | 'AUDIT_LOG_CREATED'
    | 'SECURITY_INCIDENT_CREATED';
  timestamp: string;
  data?: T;
  message?: string;
}

export interface SubscriptionPlanItem {
  id: string;
  name: string;
  description: string;
  storekitProductId: string;
  billingInterval: 'MONTHLY' | 'YEARLY' | string;
  priceCents: number;
  currency: string;
  defaultTrialDays: number;
  active: boolean;
}

export interface CouponItem {
  id: string;
  code: string;
  description?: string | null;
  couponType: 'FREE_PERIOD_DAYS' | 'PERCENT_DISCOUNT' | 'FIXED_CENTS_DISCOUNT' | string;
  discountPercent?: number | null;
  discountCents?: number | null;
  freePeriodDays?: number | null;
  applicablePlanId?: string | null;
  maxRedemptions: number;
  currentRedemptions: number;
  remainingRedemptions?: number;
  perUserLimit: number;
  restrictedToEmail?: string | null;
  firstTimeOnly?: boolean;
  expiresAtUtc?: string | null;
  expired?: boolean;
  exhausted?: boolean;
  exhaustedAtUtc?: string | null;
  active: boolean;
  createdByAdmin?: string | null;
  createdAtUtc?: string | null;
}

export interface CouponRedemptionItem {
  id: string;
  couponCode: string;
  userId: string;
  userEmail?: string | null;
  benefitSummary: string;
  redeemedAtUtc: string;
}

export interface DunningNoticeItem {
  id: string;
  subscriptionId: string;
  userId: string;
  userEmail?: string | null;
  dayNumber: number;
  noticeType: string;
  title: string;
  message: string;
  websocketDelivered: boolean;
  emailDelivered: boolean;
  acknowledgedAtUtc?: string | null;
  createdAtUtc: string;
}

export interface UserSubscriptionItem {
  id?: string | null;
  userId: string;
  userEmail: string;
  accountTier: 'FREE' | 'PRO' | string;
  planId: string;
  planName: string;
  source: string;
  status: 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'PAUSED' | 'CANCELED' | 'EXPIRED' | 'FREE' | string;
  priceCents: number;
  currency: string;
  appliedCouponCode?: string | null;
  nextBillingDiscountPercent?: number | null;
  nextBillingDiscountCents?: number | null;
  nextBillingCouponCode?: string | null;
  nextBillingPriceCents?: number | null;
  nextBillingDiscountLabel?: string | null;
  autoRenew: boolean;
  externalTransactionId?: string | null;
  currentPeriodStartUtc?: string | null;
  currentPeriodEndUtc?: string | null;
  trialEndsAtUtc?: string | null;
  overdueSinceUtc?: string | null;
  graceEndsAtUtc?: string | null;
  dunningWarningsSent: number;
  lastWarningDaySent: number;
  overdueActionEffective: 'PAUSE' | 'CANCEL' | string;
  statusReason?: string | null;
  pastDue: boolean;
  overdueDay?: number | null;
  activeWarningMessage?: string | null;
  updatedAtUtc?: string | null;
}

export interface PaymentEntryItem {
  id: string;
  userId?: string | null;
  userEmail?: string | null;
  subscriptionId?: string | null;
  entryType: string;
  status: 'PAID' | 'PENDING' | 'OVERDUE' | 'REFUNDED' | 'CANCELED' | string;
  grossAmountCents: number;
  discountCents: number;
  netAmountCents: number;
  currency: string;
  paymentMethod: string;
  couponCode?: string | null;
  referenceCode?: string | null;
  description: string;
  paidAtUtc?: string | null;
  dueAtUtc?: string | null;
  createdBy?: string | null;
  createdAtUtc: string;
}

export interface WebhookEventItem {
  id: string;
  eventId: string;
  provider: string;
  eventType: string;
  signatureValid: boolean;
  processingStatus: string;
  targetUserEmail?: string | null;
  payloadExcerpt?: string | null;
  errorMessage?: string | null;
  receivedAtUtc: string;
}

export interface WebhookConfigItem {
  endpointPath: string;
  signatureHeader: string;
  maskedSecret: string;
  rotatedPlaintextSecret?: string | null;
}

export interface DailyRevenuePoint {
  dateIso: string;
  label: string;
  revenueCents: number;
  entriesCount: number;
}

export interface BillingDashboardData {
  mrrCents: number;
  arrCents: number;
  paidRevenue30dCents: number;
  totalNetRevenueCents: number;
  pendingRevenueCents: number;
  refundedTotalCents: number;
  activeSubscribersCount: number;
  trialingCount: number;
  pastDueDay1Count: number;
  pastDueDay2Count: number;
  pastDueDay3Count: number;
  pausedCount: number;
  canceledCount: number;
  couponRedemptionsCount: number;
  overdueActionPolicy: 'PAUSE' | 'CANCEL' | string;
  graceDays: number;
  dailyRevenueLast14Days: DailyRevenuePoint[];
  plans: SubscriptionPlanItem[];
  subscriptions: UserSubscriptionItem[];
  coupons: CouponItem[];
  recentRedemptions: CouponRedemptionItem[];
  recentEntries: PaymentEntryItem[];
  recentDunningNotices: DunningNoticeItem[];
  recentWebhooks: WebhookEventItem[];
  webhookConfig: WebhookConfigItem;
}

export interface AdminAuditItem {
  id: string;
  adminActor: string;
  actionType: string;
  targetRef?: string | null;
  detailSummary?: string | null;
  createdAtUtc: string;
}

export interface SecurityIncidentItem {
  id: string;
  severity: string;
  incidentType: string;
  affectedCategories?: string | null;
  description: string;
  mitigationActions?: string | null;
  anpdNotified: boolean;
  detectedAtUtc: string;
}
