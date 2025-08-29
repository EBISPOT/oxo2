import React, { useRef, useState, useEffect, useMemo } from "react";
import ForceGraph2D from "react-force-graph-2d";
import { InferredMapping } from "../../model/Mapping";
import { hierarchy, tree} from "d3-hierarchy";

const canvasHeight = 400;

// --- Hierarchical node structure for d3.hierarchy ---
interface HierarchyNode {
    id: string;
    label: string;
    type: string;
    children?: HierarchyNode[];
}

function createMappingId(mapping: InferredMapping): string {
    const data = `${mapping.objectIri}|${mapping.predicateIri}|${mapping.subjectIri}|${mapping.mappingSetId}`;
    return data;
}

// Build a hierarchical data structure for d3.hierarchy
function buildGraphData(mapping: InferredMapping): HierarchyNode {

    function traverse(current: InferredMapping): HierarchyNode {
        const nodeId = createMappingId(current);
        const isAsserted = current.chainRuleApplications?.chainRule?.chainRuleName === "Asserted";
        const label = [
            current.subjectId ?? "",
            current.predicateId ?? "",
            current.objectId ?? ""
        ].join("\n");

        let children: HierarchyNode[] | undefined = undefined;
        const premises = current.chainRuleApplications?.premises;
        if (premises && premises.length > 0) {
            children = premises.map(premise => traverse(premise));
        }

        return {
            id: nodeId,
            label,
            type: isAsserted ? "asserted" : "inferred",
            ...(children ? { children } : {})
        };
    }

    return traverse(mapping);
}

// Convert d3.hierarchy to flat nodes/links for ForceGraph2D
function hierarchyToGraphData(root: HierarchyNode<HierarchyNode> | null) {
    const nodes: any[] = [];
    const links: any[] = [];

    if (root) {
        root.each((d: any) => {
            nodes.push({
                id: d.data.id,
                label: d.data.label,
                type: d.data.type
            });
            if (d.parent) {
                links.push({
                    source: d.parent.data.id,
                    target: d.data.id
                });
            }
        });
    }

    return { nodes, links };
}

const InferredMappingForceGraph = ({ explanation }: { explanation: InferredMapping }) => {
    // Responsive width
    const containerRef = useRef<HTMLDivElement>(null);
    const [width, setWidth] = useState<number>(800);

    useEffect(() => {
        function updateWidth() {
            if (containerRef.current) {
                setWidth(containerRef.current.offsetWidth);
            }
        }
        updateWidth();
        window.addEventListener("resize", updateWidth);
        return () => window.removeEventListener("resize", updateWidth);
    }, []);

    // Build hierarchical data and layout, update when width changes
    const { graphData } = useMemo(() => {
        const hierarchyData = buildGraphData(explanation);
        const root = hierarchyData ? hierarchy(hierarchyData) : null;

        if (root) {
            const treeLayout = tree<HierarchyNode>().size([width, canvasHeight - 40]);
            treeLayout(root);
        }

        const graphData = hierarchyToGraphData(root);

        if (root) {
            graphData.nodes.forEach(node => {
                const hNode = root.descendants().find(d => d.data.id === node.id);
                if (hNode) {
                    node.x = hNode.x;
                    node.y = hNode.y;
                    node.fx = hNode.x; // <-- fix x position
                    node.fy = hNode.y; // <-- fix y position
                }
            });
        }

        return { graphData };
    }, [explanation, width]);

    // Rectangle node renderer
    const nodeCanvasObject = (node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
        ctx.save();
        ctx.font = `${12 / globalScale}px Sans-Serif`;

        // Split label into lines and measure the longest line
        const lines = node.label.split("\n");
        const lineHeight = 14 / globalScale;
        const totalHeight = lineHeight * lines.length;
        const padding = 10;
        let maxWidth = 0;
        lines.forEach((line: string) => {
            const width = ctx.measureText(line).width;
            if (width > maxWidth) maxWidth = width;
        });
        const rectWidth = maxWidth + padding * 2;
        const rectHeight = totalHeight + padding;

        // Draw rectangle
        ctx.beginPath();
        ctx.rect(node.x - rectWidth / 2, node.y - rectHeight / 2, rectWidth, rectHeight);
        ctx.fillStyle = "#f7e7bc"; // Node color
        ctx.fill();
        ctx.strokeStyle = "#f2d890"; // Node border color
        ctx.stroke();
        ctx.closePath();

        // Draw each line of the label
        ctx.fillStyle = "#000";
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        lines.forEach((line: string, i: number) => {
            ctx.fillText(
                line,
                node.x,
                node.y - totalHeight / 2 + i * lineHeight + lineHeight / 2
            );
        });
        ctx.restore();
    };

    const fgRef = useRef<any>(null);

    return (
        <div ref={containerRef} style={{ width: "100%", height: "400px" }}>
            <h1 className="section-subheading">Explanation of inferred mapping</h1>
            <ForceGraph2D
                ref={fgRef}
                graphData={{
                    nodes: Array.isArray(graphData.nodes) ? graphData.nodes : [],
                    links: Array.isArray(graphData.links) ? graphData.links : []
                }}
                nodeLabel="label"
                linkDirectionalArrowLength={6}
                linkDirectionalArrowRelPos={1}
                linkLabel="label"
                width={width}
                height={canvasHeight}
                nodeCanvasObject={nodeCanvasObject}
                linkColor={() => "#000"}
                linkWidth={() => 2}
                enableNodeDrag={false}
                d3AlphaMin={0.001}
                onEngineStop={() => {
                    fgRef.current && fgRef.current.zoomToFit(400, 40);
                }}
            />
        </div>
    );
};

export default InferredMappingForceGraph;
