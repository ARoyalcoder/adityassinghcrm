export interface SummaryCardItem {
  id: string
  title: string
  amount: string
  trend: string
  comparison: string
  illustration: string
  illustrationAlt: string
}

export interface SpendingCategory {
  name: string
  amount: number
  displayAmount: string
  share: number
  color: string
}

export interface TransactionItem {
  id: string
  title: string
  category: string
  amount: string
  isPositive: boolean
  date: string
  iconType: 'coffee' | 'car' | 'income' | 'shopping'
  iconUrl?: string
}

export interface GoalItem {
  id: string
  title: string
  current: number
  target: number
  displayProgress: string
  percentage: number
  illustration: string
  illustrationAlt: string
  progressColor?: string
}

export const FINTRACK_DATA = {
  user: {
    name: 'Alex',
    greeting: 'Good morning! 👋',
    subtitle: "Here's what's happening with your finances today.",
    avatarUrl: '/images/fintrack/avatar-user.png',
  },
  summaryCards: [
    {
      id: 'total-balance',
      title: 'Total Balance',
      amount: '₹2,45,680',
      trend: '↑ 12.5%',
      comparison: 'vs last month',
      illustration: '/images/fintrack/wallet-coin-tr.png',
      illustrationAlt: 'Purple 3D wallet with golden coin',
    },
    {
      id: 'total-income',
      title: 'Total Income',
      amount: '₹1,20,850',
      trend: '↑ 8.3%',
      comparison: 'vs last month',
      illustration: '/images/fintrack/money-bag-tr.png',
      illustrationAlt: 'Green 3D money bag with Rupee symbol',
    },
    {
      id: 'total-expenses',
      title: 'Total Expenses',
      amount: '₹78,900',
      trend: '↑ 5.2%',
      comparison: 'vs last month',
      illustration: '/images/fintrack/shopping-bag-tr.png',
      illustrationAlt: 'Pink-red 3D shopping bag',
    },
    {
      id: 'savings-month',
      title: 'Savings This Month',
      amount: '₹41,950',
      trend: '↑ 15.8%',
      comparison: 'vs last month',
      illustration: '/images/fintrack/coins-stack-tr.png',
      illustrationAlt: 'Stack of golden coins',
    },
  ] as SummaryCardItem[],

  spendingOverview: {
    totalDisplay: '₹78,900',
    categories: [
      { name: 'Housing', amount: 25000, displayAmount: '₹25,000', share: 31, color: '#6366f1' },       // Indigo/purple
      { name: 'Food & Dining', amount: 18600, displayAmount: '₹18,600', share: 24, color: '#22c55e' }, // Green
      { name: 'Transport', amount: 12300, displayAmount: '₹12,300', share: 16, color: '#eab308' },     // Yellow/amber
      { name: 'Shopping', amount: 10500, displayAmount: '₹10,500', share: 13, color: '#f43f5e' },      // Coral/Rose
      { name: 'Entertainment', amount: 6500, displayAmount: '₹6,500', share: 8, color: '#a855f7' },   // Purple
      { name: 'Others', amount: 6000, displayAmount: '₹6,000', share: 8, color: '#3b82f6' },           // Blue
    ] as SpendingCategory[],
  },

  recentTransactions: [
    {
      id: 'tx-1',
      title: 'Starbucks Coffee',
      category: 'Food & Dining',
      amount: '-₹450',
      isPositive: false,
      date: 'Today',
      iconType: 'coffee',
      iconUrl: '/images/fintrack/tx-starbucks.png',
    },
    {
      id: 'tx-2',
      title: 'Uber Ride',
      category: 'Transport',
      amount: '-₹320',
      isPositive: false,
      date: 'Today',
      iconType: 'car',
      iconUrl: '/images/fintrack/tx-uber.png',
    },
    {
      id: 'tx-3',
      title: 'Salary from ABC Ltd.',
      category: 'Income',
      amount: '+₹85,000',
      isPositive: true,
      date: 'Yesterday',
      iconType: 'income',
      iconUrl: '/images/fintrack/tx-salary.png',
    },
    {
      id: 'tx-4',
      title: 'Amazon Shopping',
      category: 'Shopping',
      amount: '-₹1,250',
      isPositive: false,
      date: '18 May 2026',
      iconType: 'shopping',
      iconUrl: '/images/fintrack/tx-amazon.png',
    },
  ] as TransactionItem[],

  goals: [
    {
      id: 'goal-1',
      title: 'Vacation in Bali',
      current: 60000,
      target: 100000,
      displayProgress: '₹60,000 / ₹1,00,000',
      percentage: 60,
      illustration: '/images/fintrack/vacation-bali-tr.png',
      illustrationAlt: 'Tropical island palm tree',
      progressColor: '#818cf8',
    },
    {
      id: 'goal-2',
      title: 'Buy New Laptop',
      current: 45000,
      target: 80000,
      displayProgress: '₹45,000 / ₹80,000',
      percentage: 56,
      illustration: '/images/fintrack/laptop-goal-tr.png',
      illustrationAlt: 'Laptop computer',
      progressColor: '#34d399',
    },
  ] as GoalItem[],

  smartTip: {
    title: 'Smart Tip',
    message: "You're saving 15% more this month. Keep it up! 🎉",
  },
}
