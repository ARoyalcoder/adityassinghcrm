export function CharacterDeskScene() {
  return (
    <div className="relative w-full h-full flex flex-col justify-between items-end pointer-events-none select-none overflow-visible">
      {/* Top right wall decor: Top shelf plant and Picture Frame */}
      <div className="flex flex-col items-end gap-3 z-0 pointer-events-none pr-1 pt-1">
        {/* Plant on wood shelf */}
        <div className="w-[50px] h-auto drop-shadow-sm">
          <img
            src="/images/fintrack/top-plant-shelf-correct.png"
            alt="Potted plant on shelf"
            className="w-full h-auto object-contain"
          />
        </div>

        {/* 3D Scenery picture frame */}
        <div className="w-[62px] h-auto drop-shadow-sm mr-1">
          <img
            src="/images/fintrack/landscape-frame.png"
            alt="Framed landscape picture"
            className="w-full h-auto object-contain"
          />
        </div>
      </div>

      {/* 3D Seated Character working at desk */}
      <div className="relative z-10 w-[240px] flex justify-end mt-auto -mb-1">
        <img
          src="/images/fintrack/character-head-desk.png"
          alt="Friendly 3D cartoon character working on laptop"
          className="w-full h-auto object-contain drop-shadow-md"
        />
      </div>
    </div>
  )
}
