import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatCurrency } from "../utils/format";

/**
 * The Dashboard's 6-month income/expense line chart. Its own module so Dashboard can lazy() it: recharts is
 * ~360 KB, and the chart sits below the summary cards — the rest of the Dashboard no longer waits for it.
 */
export default function TrendChart({ data, incomeKey, expenseKey }) {
  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 8, bottom: 8 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="yearMonth" />
        <YAxis tickFormatter={(v) => formatCurrency(v)} width={90} />
        <Tooltip formatter={(value) => formatCurrency(value)} />
        <Legend />
        <Line type="monotone" dataKey={incomeKey} stroke="#16a34a" strokeWidth={2} />
        <Line type="monotone" dataKey={expenseKey} stroke="#dc2626" strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  );
}
