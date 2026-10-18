package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/shiva-un.ts をもとに移植
@CalloutRepo(name = "The Akh Afah Amphitheatre Unreal", duty = KnownDuty.TheAkhAfahAmphitheatreUnreal)
public class ShivaUn extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SLASHING_DOWN = 0x23C;
	private static final long BLUNT_DOWN = 0x23D;
	private static final long AVALANCHE_MARKER = 0x1A;
	private static final Duration WEAPON_CHANGE_WINDOW = Duration.ofSeconds(30);
	private static final double CENTER_TOLERANCE = 0.1;

	private XivState state;

	public ShivaUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAkhAfahAmphitheatreUnreal);
	}

	private final Set<Long> slashing = new HashSet<>();
	private final Set<Long> blunt = new HashSet<>();
	private Long currentTank;
	private Instant weaponChangedAt = Instant.MIN;
	private boolean seenDiamondDust;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		slashing.clear();
		blunt.clear();
		currentTank = null;
		weaponChangedAt = Instant.MIN;
		seenDiamondDust = false;
	}

	@HandleEvents
	public void resistGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(SLASHING_DOWN)) {
			slashing.add(event.getTarget().getId());
		}
		else if (event.buffIdMatches(BLUNT_DOWN)) {
			blunt.add(event.getTarget().getId());
		}
	}

	@HandleEvents
	public void resistLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(SLASHING_DOWN)) {
			slashing.remove(event.getTarget().getId());
		}
		else if (event.buffIdMatches(BLUNT_DOWN)) {
			blunt.remove(event.getTarget().getId());
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> staffSwap = new ModifiableCallout<>("Staff Phase (Tank Swap)", "Staff, Tank Swap");
	private final ModifiableCallout<AbilityUsedEvent> staff = new ModifiableCallout<>("Staff Phase", "Staff");
	private final ModifiableCallout<AbilityUsedEvent> swordSwap = new ModifiableCallout<>("Sword Phase (Tank Swap)", "Sword, Tank Swap");
	private final ModifiableCallout<AbilityUsedEvent> sword = new ModifiableCallout<>("Sword Phase", "Sword");
	private final ModifiableCallout<AbilityUsedEvent> frostBow = new ModifiableCallout<>("Frost Bow", "Get Behind");
	private final ModifiableCallout<AbilityUsedEvent> iceBoulder = new ModifiableCallout<>("Ice Boulder", "Free {event.target}");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5365)) {
			currentTank = event.getTarget().getId();
		}
		else if (event.abilityIdMatches(0x5367)) {
			// 現在のタンクに打属性耐性低下が残っていればスイッチ
			boolean swap = state.playerJobMatches(Job::isTank) && currentTank != null && blunt.contains(currentTank);
			context.accept((swap ? staffSwap : staff).getModified(event));
			weaponChangedAt = event.getEffectiveHappenedAt();
		}
		else if (event.abilityIdMatches(0x5366)) {
			boolean swap = state.playerJobMatches(Job::isTank) && currentTank != null && slashing.contains(currentTank);
			context.accept((swap ? swordSwap : sword).getModified(event));
			weaponChangedAt = event.getEffectiveHappenedAt();
		}
		else if (event.abilityIdMatches(0x536C)) {
			seenDiamondDust = true;
		}
		else if (event.abilityIdMatches(0x5368)) {
			seenDiamondDust = true;
			context.accept(frostBow.getModified(event));
		}
		else if (event.abilityIdMatches(0x537A) && !event.getTarget().isThePlayer()) {
			context.accept(iceBoulder.getModified(event));
		}
	}

	@PlayerHeadmarker(0x1D)
	private final ModifiableCallout<HeadMarkerEvent> hailstorm = new ModifiableCallout<>("Hailstorm", "Spread");

	@NpcCastCallout(0x5375)
	private final ModifiableCallout<AbilityCastStart> glacierBash = ModifiableCallout.durationBasedCall("Glacier Bash", "Get Behind");

	@NpcCastCallout(0x5376)
	private final ModifiableCallout<AbilityCastStart> whiteout = ModifiableCallout.durationBasedCall("Whiteout", "In");

	private final ModifiableCallout<HeadMarkerEvent> avalancheOnYou = new ModifiableCallout<>("Avalanche (On You)", "Knockback Laser on You");
	private final ModifiableCallout<HeadMarkerEvent> avalanche = new ModifiableCallout<>("Avalanche", "Avoid Laser");

	@HandleEvents
	public void avalanche(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(AVALANCHE_MARKER)) {
			context.accept((event.getTarget().isThePlayer() ? avalancheOnYou : avalanche).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> icicleImpact = new ModifiableCallout<>("Icicle Impact (Center)", "Go Middle");
	private final RepeatSuppressor icicleImpactSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void icicleImpact(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x537B) || !seenDiamondDust) {
			return;
		}
		// 武器替え直後の中央の円は対象外にして、9連続のアイシクルインパクトだけ拾う
		if (event.getEffectiveHappenedAt().isBefore(weaponChangedAt.plus(WEAPON_CHANGE_WINDOW))) {
			return;
		}
		XivCombatant source = event.getSource();
		Position pos = source.getPos();
		if (pos == null || Math.abs(pos.getX()) >= CENTER_TOLERANCE || Math.abs(pos.getY()) >= CENTER_TOLERANCE) {
			return;
		}
		if (icicleImpactSupp.check(event)) {
			context.accept(icicleImpact.getModified(event));
		}
	}

	@NpcCastCallout(0x5369)
	private final ModifiableCallout<AbilityCastStart> permafrost = ModifiableCallout.durationBasedCall("Permafrost", "Stop Moving");

}
