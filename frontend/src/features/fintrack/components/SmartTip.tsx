
interface SmartTipProps {
  title: string
  message: string
}

export function SmartTip({ title, message }: SmartTipProps) {
  return (
    <div className="relative bg-[#ebf5ea] border border-[#d6ebd5] rounded-[18px] p-2.5 px-3 flex items-center justify-between shadow-[0_2px_10px_rgba(70,120,70,0.04)] select-none overflow-hidden">
      {/* Left Lightbulb icon + text */}
      <div className="flex items-center gap-2.5">
        {/* Lightbulb 3D asset */}
        <div className="w-7 h-7 flex items-center justify-center shrink-0">
          <img
            src="/images/fintrack/smart-tip-bulb.png"
            alt="Lightbulb"
            className="w-full h-full object-contain drop-shadow-xs"
          />
        </div>

        <div>
          <h4 className="text-[11px] font-extrabold text-[#19401d] tracking-tight leading-tight">
            {title}
          </h4>
          <p className="text-[9.5px] font-medium text-[#2d5932] mt-0.5 leading-snug">
            {message}
          </p>
        </div>
      </div>

      {/* Right potted plant decoration */}
      <div className="w-8 h-8 shrink-0 flex items-center justify-center -mr-1">
        <img
          src="/images/fintrack/potted-plant-small.png"
          alt="Potted plant"
          className="w-full h-full object-contain drop-shadow-xs"
        />
      </div>
    </div>
  )
}
