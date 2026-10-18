package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/shiva-ex.ts をもとに移植
@CalloutRepo(name = "The Akh Afah Amphitheatre Extreme", duty = KnownDuty.TheAkhAfahAmphitheatreExtreme)
public class ShivaEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SLASHING_RESISTANCE_DOWN = 0x23C;
	private static final long BLUNT_RESISTANCE_DOWN = 0x23D;
	private static final Duration WEAPON_CHANGE_WINDOW = Duration.ofSeconds(30);
	private static final double CENTER_TOLERANCE = 0.1;

	private XivState state;
	private StatusEffectRepository buffs;

	public ShivaEx(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAkhAfahAmphitheatreExtreme);
	}

	private @Nullable XivCombatant currentTank;
	private @Nullable BaseEvent lastWeaponChange;
	private boolean seenDiamondDust;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		currentTank = null;
		lastWeaponChange = null;
		seenDiamondDust = false;
	}

	private boolean soonAfterWeaponChange() {
		return lastWeaponChange != null && lastWeaponChange.getEffectiveTimeSince().compareTo(WEAPON_CHANGE_WINDOW) < 0;
	}

	// 今のメインタンクに該当の耐性低下が残っていればタンクスイッチが必要
	private boolean needsTankSwap(long vulnId) {
		return state.playerJobMatches(Job::isTank)
		       && currentTank != null
		       && buffs.isStatusOnTarget(currentTank, vulnId);
	}

	private final ModifiableCallout<AbilityUsedEvent> staffTankSwap = new ModifiableCallout<>("Staff Phase (Tank Swap)", "Staff (Tank Swap)");
	private final ModifiableCallout<AbilityUsedEvent> staff = new ModifiableCallout<>("Staff Phase", "Staff");
	private final ModifiableCallout<AbilityUsedEvent> swordTankSwap = new ModifiableCallout<>("Sword Phase (Tank Swap)", "Sword (Tank Swap)");
	private final ModifiableCallout<AbilityUsedEvent> sword = new ModifiableCallout<>("Sword Phase", "Sword");
	private final ModifiableCallout<AbilityUsedEvent> frostBow = new ModifiableCallout<>("Frost Bow", "Get Behind");
	private final ModifiableCallout<AbilityUsedEvent> icicleImpact = new ModifiableCallout<>("Icicle Impact (Middle)", "Go Middle");
	private final ModifiableCallout<AbilityUsedEvent> iceBoulder = new ModifiableCallout<>("Ice Boulder", "Free {event.target}");

	// 複数人に当たるので一度だけ呼ぶ
	private final RepeatSuppressor icicleImpactSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			case 0x995 -> {
				context.accept((needsTankSwap(BLUNT_RESISTANCE_DOWN) ? staffTankSwap : staff).getModified(event));
				lastWeaponChange = event;
			}
			case 0x993 -> {
				context.accept((needsTankSwap(SLASHING_RESISTANCE_DOWN) ? swordTankSwap : sword).getModified(event));
				lastWeaponChange = event;
			}
			case 0xBE5 -> currentTank = event.getTarget();
			case 0x98A -> seenDiamondDust = true;
			case 0xBDD -> {
				context.accept(frostBow.getModified(event));
				seenDiamondDust = true;
			}
			case 0xBEB -> {
				// 中央からのアイシクルインパクトだけを拾う
				if (!seenDiamondDust || soonAfterWeaponChange()) {
					return;
				}
				Position pos = event.getSource().getPos();
				if (pos == null || Math.abs(pos.getX()) >= CENTER_TOLERANCE || Math.abs(pos.getY()) >= CENTER_TOLERANCE) {
					return;
				}
				if (icicleImpactSupp.check(event)) {
					context.accept(icicleImpact.getModified(event));
				}
			}
			case 0xC8A -> {
				if (!event.getTarget().isThePlayer()) {
					context.accept(iceBoulder.getModified(event));
				}
			}
		}
	}

	@PlayerHeadmarker(0x1D)
	private final ModifiableCallout<HeadMarkerEvent> hailstorm = new ModifiableCallout<>("Hailstorm", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> avalancheOnYou = new ModifiableCallout<>("Avalanche on You", "Knockback Laser on You");
	private final ModifiableCallout<HeadMarkerEvent> avalancheOnOther = new ModifiableCallout<>("Avalanche on Other", "Avoid Laser");

	@HandleEvents
	public void avalancheMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x1A)) {
			context.accept((event.getTarget().isThePlayer() ? avalancheOnYou : avalancheOnOther).getModified(event));
		}
	}

	@NpcCastCallout(0xBE9)
	private final ModifiableCallout<AbilityCastStart> glacierBash = ModifiableCallout.durationBasedCall("Glacier Bash", "Get Behind");

	@NpcCastCallout(0xBEC)
	private final ModifiableCallout<AbilityCastStart> whiteout = ModifiableCallout.durationBasedCall("Whiteout", "In");

	@NpcCastCallout(0xBE3)
	private final ModifiableCallout<AbilityCastStart> permafrost = ModifiableCallout.durationBasedCall("Permafrost", "Stop Moving");

}
