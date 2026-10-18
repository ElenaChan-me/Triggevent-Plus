package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_lunar_subterrane.ts をもとに移植
@CalloutRepo(name = "The Lunar Subterrane", duty = KnownDuty.TheLunarSubterrane)
public class TheLunarSubterrane extends AutoChildEventHandler implements FilteredEventHandler {

	// ダークエルフ戦のフィールド中心
	private static final ArenaPos elfArena = new ArenaPos(-401.02, -231.01, 1, 1);
	// 内側4マスの列と行。杖が置かれていない列・行の交点が安置になる
	private static final List<Integer> innerColumns = List.of(-405, -397);
	private static final List<Integer> innerRows = List.of(-235, -227);
	// 3回目以降のフォーセイクン・ファウントで玉が出現しうる座標
	private static final double[][] fountSpawns = {
			{0, -422}, {0, -434.8}, {0, -409.2},
			{3.65, -412.9}, {-3.65, -412.95}, {9.05, -412.8}, {-9.05, -418.35},
			{9.15, -425.55}, {9.15, -431.1}, {-9.25, -431.1},
			{12.8, -422}, {-12.8, -422},
	};
	private static final double FOUNT_SPAWN_TOLERANCE = 0.5;
	private static final double FOUNT_CENTER_Y = -422;
	private static final int FOUNT_ORB_COUNT = 5;
	private static final int FOUNT_LINE_COUNT = 3;

	private XivState state;

	public TheLunarSubterrane(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheLunarSubterrane);
	}

	private final List<Position> staffPositions = new ArrayList<>();
	private final List<Position> fountPositions = new ArrayList<>();
	private int fountsSeen;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		staffPositions.clear();
		fountPositions.clear();
		fountsSeen = 0;
	}

	private @Nullable Position posOf(XivCombatant cbt) {
		Position pos = cbt.getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(cbt).getPos();
		}
		return pos;
	}

	// ダークエルフ

	@NpcCastCallout(0x87DE)
	private final ModifiableCallout<AbilityCastStart> abyssalOutburst = ModifiableCallout.durationBasedCall("Abyssal Outburst", "Raidwide");

	@NpcCastCallout(value = 0x87E4, onYou = true)
	private final ModifiableCallout<AbilityCastStart> voidDarkII = ModifiableCallout.durationBasedCall("Void Dark II", "Spread");

	@NpcCastCallout(0x8984)
	private final ModifiableCallout<AbilityCastStart> staffSmite = ModifiableCallout.durationBasedCall("Staff Smite", "Buster on {event.target}");

	@NpcCastCallout(0x87DB)
	private final ModifiableCallout<AbilityCastStart> shadowSigilPink = ModifiableCallout.durationBasedCall("Shadow Sigil (Pink Triangle)", "Blue Square Safe");

	@NpcCastCallout(0x87DC)
	private final ModifiableCallout<AbilityCastStart> shadowSigilBlue = ModifiableCallout.durationBasedCall("Shadow Sigil (Blue Square)", "Pink Triangle Safe");

	@HandleEvents
	public void ruinousHexCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x89B6, 0x87DF)) {
			Position pos = posOf(event.getSource());
			if (pos != null) {
				staffPositions.add(pos);
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> ruinousHex = new ModifiableCallout<>("Ruinous Hex", "Inner {safe} Safe");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ruinousHexSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8985),
			(e1, s) -> {
				s.waitMs(500);
				// 内側には必ず安置が1マスあるので、杖のない列と行の交点だけを見ればよい
				List<Integer> safeX = new ArrayList<>(innerColumns);
				List<Integer> safeY = new ArrayList<>(innerRows);
				for (Position staff : staffPositions) {
					safeX.remove(Integer.valueOf((int) Math.round(staff.getX())));
					safeY.remove(Integer.valueOf((int) Math.round(staff.getY())));
				}
				staffPositions.clear();
				if (safeX.isEmpty() || safeY.isEmpty()) {
					return;
				}
				ArenaSector safe = elfArena.forPosition(Position.of2d(safeX.get(0), safeY.get(0)));
				if (!safe.isIntercard()) {
					return;
				}
				s.setParam("safe", safe);
				s.updateCall(ruinousHex, e1);
			});

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

	private final ModifiableCallout<BuffApplied> doom = new ModifiableCallout<BuffApplied>("Doom", "Cleanse {event.target}").autoIcon();

	@HandleEvents
	public void doom(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xD24) && canCleanse()) {
			context.accept(doom.getModified(event));
		}
	}

	// ダムキアン・アントリオン

	@NpcCastCallout(0x87FD)
	private final ModifiableCallout<AbilityCastStart> sandblast = ModifiableCallout.durationBasedCall("Sandblast", "Raidwide");

	@NpcCastCallout(0x8806)
	private final ModifiableCallout<AbilityCastStart> earthenGeyser = ModifiableCallout.durationBasedCall("Earthen Geyser", "Stack on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> poundSand = new ModifiableCallout<>("Pound Sand", "Out");

	@HandleEvents
	public void poundSand(EventContext context, AbilityUsedEvent event) {
		// 頭割りの着弾地点に続けて範囲が来るので、対象者は離れる
		if (event.abilityIdMatches(0x8806) && event.getTarget().isThePlayer()) {
			context.accept(poundSand.getModified(event));
		}
	}

	// ドゥランテ

	@NpcCastCallout(0x88C3)
	private final ModifiableCallout<AbilityCastStart> oldMagic = ModifiableCallout.durationBasedCall("Old Magic", "Raidwide");

	@NpcCastCallout(value = 0x8C2A, onYou = true)
	private final ModifiableCallout<AbilityCastStart> fallenGrace = ModifiableCallout.durationBasedCall("Fallen Grace", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> antipodalAssault = new ModifiableCallout<>("Antipodal Assault", "Stack on {event.target}");

	@HandleEvents
	public void antipodalAssault(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x38FC) && event.isFirstTarget()) {
			context.accept(antipodalAssault.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x88C0, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> hardSlash = new ModifiableCallout<>("Hard Slash", "Go Sides on Wall");

	@NpcCastCallout(0x8CD8)
	private final ModifiableCallout<AbilityCastStart> twilightPhase = ModifiableCallout.durationBasedCall("Twilight Phase", "Sides");

	@NpcCastCallout(0x88BA)
	private final ModifiableCallout<AbilityCastStart> darkImpact = ModifiableCallout.durationBasedCall("Dark Impact", "Get Behind and Out");

	@NpcCastCallout(0x88C2)
	private final ModifiableCallout<AbilityCastStart> arcaneEdge = ModifiableCallout.durationBasedCall("Arcane Edge", "Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> forsakenFount1 = new ModifiableCallout<>("Forsaken Fount 1", "Away from Orbs");
	private final ModifiableCallout<AbilityUsedEvent> forsakenFount2 = new ModifiableCallout<>("Forsaken Fount 2", "Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forsakenFountSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x88BB) && fountsSeen < 2,
			(e1, s) -> {
				// 1回目は分裂しない玉の列、2回目は分裂する列で中央が安置と決まっている
				int round = ++fountsSeen;
				s.waitMs(1_000);
				s.updateCall(round == 1 ? forsakenFount1 : forsakenFount2, e1);
			});

	private static @Nullable Position addedPos(RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		if (info != null && info.getPos() != null) {
			return info.getPos();
		}
		return event.getEntity().getPos();
	}

	// 名前ではなく、既知の出現座標と一致するかどうかで玉を判別する
	private boolean isFountOrb(RawAddCombatantEvent event) {
		if (fountsSeen < 2 || event.getEntity().isPc()) {
			return false;
		}
		Position pos = addedPos(event);
		if (pos == null) {
			return false;
		}
		for (double[] spawn : fountSpawns) {
			if (Math.abs(pos.getX() - spawn[0]) < FOUNT_SPAWN_TOLERANCE && Math.abs(pos.getY() - spawn[1]) < FOUNT_SPAWN_TOLERANCE) {
				return true;
			}
		}
		return false;
	}

	@HandleEvents
	public void fountCollect(EventContext context, RawAddCombatantEvent event) {
		if (isFountOrb(event)) {
			fountPositions.add(addedPos(event));
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> forsakenFount3 = new ModifiableCallout<>("Forsaken Fount 3+", "{safe} Safe");
	private final ModifiableCallout<RawAddCombatantEvent> forsakenFount3Middle = new ModifiableCallout<>("Forsaken Fount 3+ (Hourglass)", "Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forsakenFount3Sq = SqtTemplates.sq(5_000,
			RawAddCombatantEvent.class, this::isFountOrb,
			(e1, s) -> {
				s.waitMs(500);
				List<Position> orbs = new ArrayList<>(fountPositions);
				fountPositions.clear();
				if (orbs.size() < FOUNT_ORB_COUNT) {
					return;
				}
				List<Long> xs = orbs.stream().map(pos -> Math.round(pos.getX())).toList();
				// 中心のY座標を0として扱う。正の値が南
				List<Long> ys = orbs.stream().map(pos -> Math.round(pos.getY() - FOUNT_CENTER_Y)).toList();
				long hCount = ys.stream().filter(n -> n == 0).count();
				long vCount = xs.stream().filter(n -> n == 0).count();
				if (hCount == FOUNT_LINE_COUNT) {
					// 横一列のときは東西どちらかが安置で、残り2つの玉がある側の反対になる
					long xSum = xs.stream().mapToLong(Long::longValue).sum();
					if (xSum > 5) {
						s.setParam("safe", ArenaSector.WEST);
					}
					else if (xSum < 5) {
						s.setParam("safe", ArenaSector.EAST);
					}
					else {
						return;
					}
					s.updateCall(forsakenFount3, e1);
				}
				else if (vCount == FOUNT_LINE_COUNT) {
					// 縦一列のときは、合計が0付近なら砂時計型で中央が安置
					long ySum = ys.stream().mapToLong(Long::longValue).sum();
					if (ySum > 10) {
						s.setParam("safe", ArenaSector.NORTH);
						s.updateCall(forsakenFount3, e1);
					}
					else if (ySum < -10) {
						s.setParam("safe", ArenaSector.SOUTH);
						s.updateCall(forsakenFount3, e1);
					}
					else {
						s.updateCall(forsakenFount3Middle, e1);
					}
				}
			});

}
